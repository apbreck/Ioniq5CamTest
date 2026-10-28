package dev.cameraprobe;

import android.graphics.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Only a manually selected, leased, parked camera session. No CAN writes, power or view-mode setters. */
public final class Worker {
    private final Handler main=new Handler();
    private volatile boolean active=false, opening=false;
    private volatile long releasingSince=0;
    private volatile long guardBeat=SystemClock.elapsedRealtime();
    private volatile long mainBeat=SystemClock.elapsedRealtime(), blockedSince=0;
    private Object camera, callback;
    private Class<?> cameraClass;
    private SurfaceTexture texture;
    private String session="", lastAttempt="", reason="Idle — choose a feed and press Start", api="";
    private int id=-1,width,height,format;
    private long started,lastFrame,lastJpeg,frames,changes,previousHash,durationMs;
    private String assessment="No frames observed", jpegError="", parameters="";
    private double mean,variance;
    private int min,max;
    private JSONArray catalogue=new JSONArray();
    private volatile String gate="Checking vehicle state";
    private volatile boolean allowed=false;
    private boolean emulated=false;
    private volatile boolean exporting=false;
    private String lastExport="";
    private String phase="inventory_only";
    private boolean openConfirmed=false,previewStartConfirmed=false;

    public static void main(String[] args) throws Exception {
        boolean test=args.length==1 && ("--emulator".equals(args[0])||"--emulator-inventory".equals(args[0]));
        // Test bypass is accepted only on a genuine Android emulator, never on the vehicle.
        if(test && !("1".equals(prop("ro.kernel.qemu"))))throw new SecurityException("Emulator-only flag refused");
        if(!test) dropRoot();
        Looper.prepareMainLooper();
        if(test){
            Class<?> os=Class.forName("android.system.Os");os.getMethod("setgid",int.class).invoke(null,1028);os.getMethod("setuid",int.class).invoke(null,1000);
            // Newer emulator Camera implementations require an Application context; Gen5W does not.
            Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
            android.content.Context context=(android.content.Context)at.getMethod("getSystemContext").invoke(thread);
            android.app.Application app=new android.app.Application();
            Method attach=android.app.Application.class.getDeclaredMethod("attach",android.content.Context.class);attach.setAccessible(true);attach.invoke(app,context);
            Field initial=at.getDeclaredField("mInitialApplication");initial.setAccessible(true);initial.set(thread,app);
        }
        try{android.os.Process.class.getMethod("setArgV0",String.class).invoke(null,"camera-probe-worker");}catch(Throwable ignored){}
        Worker w=new Worker();w.emulated=test&&"--emulator".equals(args[0]);w.start();Looper.loop();
    }
    static String prop(String key) {
        try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,key);}catch(Throwable t){return "";}
    }
    private static void dropRoot() throws Exception {
        Object os=Class.forName("libcore.io.Libcore").getField("os").get(null);
        if((Integer)os.getClass().getMethod("getuid").invoke(os)==0){
            // Gen5W's USB FUSE volume is writable by sdcard_r (same group used by Rear Dashcam).
            os.getClass().getMethod("setgid",int.class).invoke(os,1028);
            os.getClass().getMethod("setuid",int.class).invoke(os,1000);
        }
        if(android.os.Process.myUid()!=1000)throw new SecurityException("Worker must run as system UID 1000");
    }
    private static Object service(String name,String stub) throws Exception {
        Object binder=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
        if(binder==null)throw new IllegalStateException("Missing "+name);
        return Class.forName(stub+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    }
    private boolean checkGate() {
        if(emulated){gate="EMULATOR — synthetic feed; no vehicle checks";return true;}
        try {
            Object reg=service("com.hkmc.regulation","com.hkmc.regulation.IRegulationManager");
            int status=(Integer)Class.forName("com.hkmc.regulation.IRegulationManager").getMethod("getStatus").invoke(reg);
            if(status!=0){gate="Blocked: vehicle is outside Park";return false;}
            Object bcm=service("com.hkmc.micom.bcm","com.hkmc.bcm.IBCMService");
            if(!(Boolean)Class.forName("com.hkmc.bcm.IBCMService").getMethod("getIGNStatus").invoke(bcm)){gate="Blocked: ignition is off";return false;}
            int gear=(Integer)Class.forName("android.hardware.RearCamera").getMethod("getGearStatus").invoke(null);
            if(gear<0 || gear==1){gate="Blocked: factory camera requested, reverse, or unknown status ("+gear+")";return false;}
            Object am=Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
            Class<?> receiver=Class.forName("android.app.IThumbnailReceiver");
            @SuppressWarnings("unchecked") List<android.app.ActivityManager.RunningTaskInfo> tasks=(List<android.app.ActivityManager.RunningTaskInfo>)Class.forName("android.app.IActivityManager").getMethod("getTasks",int.class,int.class,receiver).invoke(am,1,0,null);
            if(tasks==null||tasks.isEmpty()||tasks.get(0).topActivity==null||!"dev.cameraprobe".equals(tasks.get(0).topActivity.getPackageName())){
                gate="Blocked: Camera Feed Probe is not the foreground app";return false;
            }
            gate="Park confirmed • ignition on • factory camera not requested";return true;
        }catch(Throwable t){gate="Blocked: vehicle state unavailable — "+Files.error(t);return false;}
    }
    private void start() throws Exception {
        if(!emulated)reason="Vehicle preview disabled: unsupported camera-driver path. Inventory and USB export are available; no camera open attempted.";
        try{lastAttempt=Files.read(new File(Files.ROOT,"last-attempt")).trim();}catch(Exception ignored){}
        try{lastExport=Files.read(new File(Files.ROOT,"last-export")).trim();}catch(Exception ignored){}
        enumerate();
        new Thread(new Runnable(){public void run(){while(true){
            long n=SystemClock.elapsedRealtime();
            if(n-mainBeat>8000 || ((active||opening)&&n-guardBeat>1500) || (releasingSince!=0&&n-releasingSince>800))android.os.Process.killProcess(android.os.Process.myPid());
            SystemClock.sleep(100);
        }}},"camera-probe-reaper").start();
        new Thread(new Runnable(){public void run(){
            while(true){
                long now=SystemClock.elapsedRealtime();
                if(releasingSince!=0 && now-releasingSince>800)android.os.Process.killProcess(android.os.Process.myPid());
                boolean lease=freshCommand(now)!=null;
                allowed=lease && checkGate();
                guardBeat=SystemClock.elapsedRealtime();
                if(!lease){gate="No active preview request";}
                if((active||opening) && !allowed){
                    if(blockedSince==0){blockedSince=now;main.post(new Runnable(){public void run(){stop(gate);}});}
                    // Free native camera handles even if the HAL blocks its release call.
                    if(now-blockedSince>800)android.os.Process.killProcess(android.os.Process.myPid());
                }else blockedSince=0;
                if(now-mainBeat>8000)android.os.Process.killProcess(android.os.Process.myPid());
                SystemClock.sleep(100);
            }
        }},"camera-probe-guard").start();
        main.post(tick);
    }
    private JSONObject freshCommand(long now){
        try {
            JSONObject q=Files.json("request.json");long age=now-q.getLong("renewed");
            if(age<0 || age>2000 || !"start".equals(q.optString("action")))return null;
            String s=q.optString("session"); if(!s.matches("[a-zA-Z0-9-]{1,80}"))return null;
            return q;
        }catch(Throwable t){return null;}
    }
    private final Runnable tick=new Runnable(){public void run(){
        mainBeat=SystemClock.elapsedRealtime();
        try {
            JSONObject q=freshCommand(mainBeat);
            if(q==null){if(active)stop("Stopped — preview request ended");}
            else if(active && !q.optString("session").equals(session))stop("Stopped — selection changed");
            if(q!=null && !active && !opening && !q.optString("session").equals(lastAttempt)){
                // Consume once before native open: a failed/hung worker never retries unattended.
                lastAttempt=q.getString("session");Files.write(new File(Files.ROOT,"last-attempt"),lastAttempt.getBytes("UTF-8"));
                session=lastAttempt;
                api=q.optString("api");id=q.optInt("id",-1);openConfirmed=previewStartConfirmed=false;
                if(!emulated){phase="capture_disabled";reason="Vehicle preview disabled: unsupported camera-driver path. No camera open attempted.";recordStage();}
                else{allowed=checkGate();if(allowed)open(q);else{phase="blocked";reason=gate+". Press Start again after resolving this.";recordStage();}}
            }
            if(active && mainBeat-started>=30000)stop("30-second test finished — press Start for another test");
            checkExport();
            publish();
        }catch(Throwable t){stop("Test error — "+Files.error(t));}
        finally {mainBeat=SystemClock.elapsedRealtime();main.postDelayed(this,200);}
    }};
    private void checkExport(){
        if(exporting)return;
        try{
            JSONObject q=Files.json("export-request.json");
            final String requestId=q.getString("id");long age=SystemClock.elapsedRealtime()-q.getLong("created");
            if(age<0||age>30000||!requestId.matches("[a-zA-Z0-9-]{1,80}")||requestId.equals(lastExport))return;
            if(active)stop("Stopped for USB report export");
            final JSONObject report=q.getJSONObject("report");
            report.put("lastStatus",state());
            try{report.put("sessionHistoryJsonl",Files.read(new File(Files.ROOT,"sessions.jsonl")));}catch(Exception ignored){}
            try{report.put("setupEventsJsonl",Files.read(new File(Files.ROOT,"setup-events.jsonl")));}catch(Exception ignored){}
            lastExport=requestId;Files.write(new File(Files.ROOT,"last-export"),requestId.getBytes("UTF-8"));
            exporting=true;
            new Thread(new Runnable(){public void run(){
                JSONObject result=new JSONObject();
                try{
                    result.put("id",requestId);
                    String stamp=new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.US).format(new java.util.Date());
                    String name="camera-probe-"+stamp+"-"+requestId.substring(0,Math.min(8,requestId.length()))+".json";
                    File[] roots={new File("/storage/usb0"),new File("/storage/usb1"),new File("/storage/usb2")};
                    File dest=UsbReportWriter.write(roots,Files.read(new File("/proc/mounts")),name,report.toString(2).getBytes("UTF-8"));
                    result.put("success",true);result.put("path",dest.getPath());
                }catch(Throwable t){try{result.put("success",false);result.put("error",Files.error(t));}catch(Exception ignored){}}
                finally{try{Files.write("export-result.json",result);}catch(Exception ignored){}exporting=false;}
            }},"camera-probe-usb-export").start();
        }catch(Throwable ignored){}
    }
    private void enumerate() {
        catalogue=new JSONArray();
        for(String name:Files.APIS){
            try {
                Class<?> c=Class.forName(name);int n=(Integer)c.getMethod("getNumberOfCameras").invoke(null);
                n=Math.max(0,Math.min(16,n));
                if(n==0){JSONObject r=new JSONObject();r.put("api",name);r.put("error","Interface reports zero cameras");catalogue.put(r);}
                for(int i=0;i<n;i++){
                    JSONObject r=new JSONObject();r.put("api",name);r.put("id",i);
                    r.put("identity",name+" #"+i);r.put("physicalCamera","Unidentified — verify visually");
                    try{
                        Class<?> info=Class.forName(name+(name.endsWith("RearCamera")?"$RearCameraInfo":"$CameraInfo"));
                        Object v=info.newInstance();c.getMethod("getCameraInfo",int.class,info).invoke(null,i,v);
                        r.put("facing",info.getField("facing").getInt(v));r.put("orientation",info.getField("orientation").getInt(v));
                    }catch(Throwable t){r.put("metadataError",Files.error(t));}
                    catalogue.put(r);
                }
            }catch(Throwable t){try{JSONObject r=new JSONObject();r.put("api",name);r.put("error",Files.error(t));catalogue.put(r);}catch(Exception ignored){}}
        }
        try{JSONObject o=new JSONObject();o.put("feeds",catalogue);o.put("firmware",prop("ro.build.product2"));Files.write("catalogue.json",o);}catch(Exception ignored){}
    }
    private Object call(String name,Class<?>[] types,Object... args)throws Exception{return cameraClass.getMethod(name,types).invoke(camera,args);}
    private void markStage(String next){phase=next;publish();recordStage();}
    private void recordStage(){
        try{
            File f=new File(Files.ROOT,"setup-events.jsonl");
            if(f.length()>256*1024){File old=new File(Files.ROOT,"setup-events.previous.jsonl");old.delete();f.renameTo(old);}
            JSONObject event=state();event.put("recordedAt",System.currentTimeMillis());
            try(FileOutputStream out=new FileOutputStream(f,true)){out.write((event.toString()+"\n").getBytes("UTF-8"));out.getFD().sync();}f.setReadable(true,false);
        }catch(Throwable ignored){}
    }
    private void open(JSONObject q){
        try {
            if(!emulated)throw new SecurityException("Vehicle camera capture disabled pending a compatible implementation");
            api=q.getString("api");id=q.getInt("id");
            if(!Arrays.asList(Files.APIS).contains(api))throw new IllegalArgumentException("Unsupported API");
            cameraClass=Class.forName(api);int count=(Integer)cameraClass.getMethod("getNumberOfCameras").invoke(null);
            if(id<0||id>=count)throw new IllegalArgumentException("Camera ID out of range");
            frames=changes=0;previousHash=0;lastFrame=lastJpeg=started=durationMs=0;min=max=0;mean=variance=0;parameters="";jpegError="";assessment="Waiting for callbacks";
            new File(Files.ROOT,"preview.jpg").delete();opening=true;
            if(!checkGate())throw new IllegalStateException(gate);
            markStage("opening_camera");camera=cameraClass.getMethod("open",int.class).invoke(null,id);
            if(camera==null)throw new IllegalStateException("Camera open returned null");
            openConfirmed=true;markStage("reading_parameters");
            Object params=call("getParameters",new Class<?>[0]);Class<?> pc=params.getClass();
            parameters=(String)pc.getMethod("flatten").invoke(params);
            @SuppressWarnings("unchecked") List<Integer> formats=(List<Integer>)pc.getMethod("getSupportedPreviewFormats").invoke(params);
            int chosen=formats!=null&&formats.contains(17)?17:formats!=null&&formats.contains(20)?20:(Integer)pc.getMethod("getPreviewFormat").invoke(params);
            pc.getMethod("setPreviewFormat",int.class).invoke(params,chosen);
            @SuppressWarnings("unchecked") List<Object> sizes=(List<Object>)pc.getMethod("getSupportedPreviewSizes").invoke(params);
            String wanted=q.optString("size","auto");Object pick=null;
            long best=Long.MAX_VALUE;
            if(sizes!=null)for(Object s:sizes){
                int w=s.getClass().getField("width").getInt(s),h=s.getClass().getField("height").getInt(s);
                if(w<64||h<64||(long)w*h>1280*720)continue;
                if(!"auto".equals(wanted)){if((w+"x"+h).equals(wanted)){pick=s;break;}}
                else{long score=Math.abs((long)w*h-720*480);if(score<best){best=score;pick=s;}}
            }
            if(pick==null)throw new IllegalStateException("No supported size at or below 1280x720 matching "+wanted);
            width=pick.getClass().getField("width").getInt(pick);height=pick.getClass().getField("height").getInt(pick);
            pc.getMethod("setPreviewSize",int.class,int.class).invoke(params,width,height);
            markStage("configuring_parameters");call("setParameters",new Class<?>[]{pc},params);
            params=call("getParameters",new Class<?>[0]);parameters=(String)pc.getMethod("flatten").invoke(params);
            Object sz=pc.getMethod("getPreviewSize").invoke(params);
            width=sz.getClass().getField("width").getInt(sz);height=sz.getClass().getField("height").getInt(sz);
            format=(Integer)pc.getMethod("getPreviewFormat").invoke(params);
            if((long)width*height>1280*720)throw new IllegalStateException("HAL returned oversized preview");
            texture=new SurfaceTexture(0); // Offscreen sink; JPEG thumbnails are shown by the UI.
            markStage("attaching_preview_sink");call("setPreviewTexture",new Class<?>[]{SurfaceTexture.class},texture);
            Class<?> cb=Class.forName(api+"$PreviewCallback");
            callback=Proxy.newProxyInstance(cb.getClassLoader(),new Class<?>[]{cb},new InvocationHandler(){
                public Object invoke(Object proxy,Method method,Object[] args){
                    if("onPreviewFrame".equals(method.getName())&&active&&args!=null&&args[0] instanceof byte[]){
                        try{frame((byte[])args[0]);}finally{if(active&&camera!=null)try{call("addCallbackBuffer",new Class<?>[]{byte[].class},args[0]);}catch(Exception ignored){}}
                    }
                    if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
                    if("equals".equals(method.getName()))return proxy==args[0];
                    if("toString".equals(method.getName()))return "CameraProbeCallback";
                    return null;
                }
            });
            call("setPreviewCallbackWithBuffer",new Class<?>[]{cb},callback);
            int bufferBytes;
            if(format==20)bufferBytes=width*height*2;
            else if(format==842094169){int ystride=(width+15)/16*16;int cstride=(ystride/2+15)/16*16;bufferBytes=ystride*height+2*cstride*((height+1)/2);}
            else bufferBytes=(width*height*ImageFormat.getBitsPerPixel(format)+7)/8;
            if(bufferBytes<=0||bufferBytes>4*1024*1024)throw new IllegalStateException("Unsupported buffer layout");
            for(int i=0;i<3;i++)call("addCallbackBuffer",new Class<?>[]{byte[].class},new byte[bufferBytes]);
            started=SystemClock.elapsedRealtime();active=true;reason="Preview running";
            if(!checkGate())throw new IllegalStateException(gate);
            markStage("starting_preview");call("startPreview",new Class<?>[0]);previewStartConfirmed=true;opening=false;markStage("waiting_for_frames");
        }catch(Throwable t){stop("Could not preview — "+Files.error(t));}
    }
    private void frame(byte[] data){
        long now=SystemClock.elapsedRealtime();frames++;lastFrame=now;
        if(frames==1)markStage("frames_received");
        if(now-lastJpeg<500)return;lastJpeg=now;
        try {
            FrameStats s=FrameStats.analyze(data,width,height,format);assessment=s.verdict();
            min=s.min;max=s.max;mean=s.mean;variance=s.variance;
            if(previousHash!=0&&s.fingerprint!=previousHash)changes++;previousHash=s.fingerprint;
            if(format==17||format==20||format==842094169){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] pixels=format==842094169?FrameStats.yv12ToNv21(data,width,height):data;
                if(!new YuvImage(pixels,format==842094169?17:format,width,height,null).compressToJpeg(new Rect(0,0,width,height),65,out))throw new IOException("JPEG conversion failed");
                Files.write(new File(Files.ROOT,"preview.jpg"),out.toByteArray());jpegError="";
            }else jpegError="This pixel format has statistics but no JPEG converter";
        }catch(Throwable t){assessment="Callbacks arriving; pixel interpretation unconfirmed";jpegError=Files.error(t);}
    }
    private void stop(String why){
        boolean attempted=active||opening||camera!=null;
        if(attempted&&started>0)durationMs=SystemClock.elapsedRealtime()-started;
        active=false;releasingSince=SystemClock.elapsedRealtime();
        try{if(camera!=null){try{call("stopPreview",new Class<?>[0]);}catch(Throwable ignored){}call("release",new Class<?>[0]);}}
        catch(Throwable t){why+="; release: "+Files.error(t);}
        finally{camera=null;callback=null;if(texture!=null){texture.release();texture=null;}opening=false;releasingSince=0;}
        reason=why;
        phase=why.startsWith("Could not preview")?"failed":"stopped";recordStage();
        new File(Files.ROOT,"preview.jpg").delete();
        if(attempted)try{JSONObject r=state();r.put("endedAt",System.currentTimeMillis());
            File log=new File(Files.ROOT,"sessions.jsonl");
            if(log.length()>512*1024){File old=new File(Files.ROOT,"sessions.previous.jsonl");old.delete();log.renameTo(old);}
            try(FileOutputStream out=new FileOutputStream(log,true)){out.write((r.toString()+"\n").getBytes("UTF-8"));}log.setReadable(true,false);
        }catch(Throwable ignored){}
    }
    private JSONObject state()throws Exception{
        JSONObject s=new JSONObject();long now=SystemClock.elapsedRealtime();
        s.put("updated",now);s.put("active",active);s.put("session",session);s.put("api",api);s.put("id",id);
        s.put("reason",reason);s.put("gate",gate);s.put("frames",frames);s.put("sampleChanges",changes);
        s.put("phase",phase);s.put("opening",opening);s.put("openConfirmed",openConfirmed);s.put("previewStartConfirmed",previewStartConfirmed);s.put("captureEnabled",emulated);
        s.put("width",width);s.put("height",height);s.put("format",format);
        long duration=active?now-started:durationMs;s.put("durationMs",duration);
        s.put("fps",(double)frames/Math.max(0.001,duration/1000.0));
        s.put("lastFrameAgeMs",lastFrame==0?-1:now-lastFrame);
        s.put("evidence",active&&lastFrame==0&&now-started>3000?"No callback frames yet — feed may use a different hardware path":assessment);
        s.put("meanY",mean);s.put("varianceY",variance);s.put("minY",min);s.put("maxY",max);
        s.put("previewNote",jpegError);s.put("parameters",parameters);s.put("emulator","1".equals(prop("ro.kernel.qemu")));s.put("probeVersion","0.1.2");
        s.put("secondsLeft",active?Math.max(0,30-(now-started)/1000):0);return s;
    }
    private void publish(){try{Files.write("status.json",state());}catch(Throwable ignored){}}
}
