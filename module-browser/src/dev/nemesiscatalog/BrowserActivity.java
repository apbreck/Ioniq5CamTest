package dev.nemesiscatalog;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class BrowserActivity extends Activity {
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler();
    private final ArrayList<Module> modules=new ArrayList<Module>();
    private ListView list;
    private TextView details,status,summary;
    private Button refresh,download,install,check;
    private Module selected;
    private File verified;
    private boolean busy=false,test=false,alive=true;
    private String catalogUrl=Policy.CATALOG,firmware="";
    private File pending;
    private interface Job{void run()throws Exception;}
    public void onCreate(Bundle saved){
        super.onCreate(saved);test=getIntent().getBooleanExtra("catalog.emulatorTest",false)&&"1".equals(prop("ro.kernel.qemu"));
        if(test){String url=getIntent().getStringExtra("catalog.testUrl");try{if(url!=null&&Policy.local(new java.net.URL(url)))catalogUrl=url;}catch(Exception ignored){}}
        firmware=prop("ro.build.product2");pending=new File(getFilesDir(),"install-pending.json");
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,12,20,12);root.setBackgroundColor(0xff101a29);setContentView(root);
        root.addView(text("Nemesis Module Browser",26,0xffedf4ff));
        summary=text((test?"EMULATOR TEST • ":"")+"Test catalog: "+Policy.REPO+" • Experimental",15,0xffacc4dc);root.addView(summary);
        LinearLayout body=new LinearLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(this);body.addView(list,new LinearLayout.LayoutParams(430,-1));
        ScrollView scroll=new ScrollView(this);LinearLayout right=new LinearLayout(this);right.setOrientation(1);right.setPadding(22,4,0,0);scroll.addView(right);body.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        details=text("Refresh the catalog to browse modules. Downloads are limited to this GitHub repository.",18,0xffedf4ff);right.addView(details);
        LinearLayout actions=new LinearLayout(this);right.addView(actions);download=button("Download & verify");install=button("Install module");actions.addView(download);actions.addView(install);
        status=text("Ready",17,0xffb8d4ed);right.addView(status);
        LinearLayout footer=new LinearLayout(this);root.addView(footer);refresh=button("Refresh catalog");check=button("Check installed");Button logs=button("View log");footer.addView(refresh);footer.addView(check);footer.addView(logs);
        refresh.setOnClickListener(v->loadCatalog());check.setOnClickListener(v->checkInstalled());logs.setOnClickListener(v->{try{show("Operation log",read(new File(getFilesDir(),"browser.log")));}catch(Exception e){show("Operation log","No operations logged yet.");}});
        download.setOnClickListener(v->download());install.setOnClickListener(v->confirmInstall());
        list.setOnItemClickListener((p,v,pos,id)->{if(!busy){selected=modules.get(pos);verified=null;describe("");buttons();}});
        buttons();loadCatalog();
    }
    static String prop(String name){try{return(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,name);}catch(Exception e){return "";}}
    private TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(4,6,4,6);return v;}
    private Button button(String name){Button b=new Button(this);b.setText(name);b.setTextSize(16);return b;}
    private void show(String title,String message){if(alive)new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private void say(final String s){ui.post(()->{if(alive)status.setText(s);});}
    private void log(String s){try{File f=new File(getFilesDir(),"browser.log");if(f.length()>128*1024){File old=new File(getFilesDir(),"browser.previous.log");old.delete();f.renameTo(old);}try(FileOutputStream o=new FileOutputStream(f,true)){o.write((new Date()+" "+s+"\n").getBytes("UTF-8"));}}catch(Exception ignored){}}
    private boolean matches(){try{return selected!=null&&selected.matches(firmware);}catch(Exception e){return false;}}
    private void buttons(){refresh.setEnabled(!busy);check.setEnabled(!busy);list.setEnabled(!busy);download.setEnabled(!busy&&selected!=null&&matches());install.setEnabled(!busy&&verified!=null&&matches()&&!pending.exists());}
    private void job(String label,Job action){if(busy)return;busy=true;buttons();say(label);jobs.execute(()->{try{action.run();}catch(Exception e){String message=e.getClass().getSimpleName()+": "+e.getMessage();log(message);say(message);ui.post(()->show("Operation did not complete",message));}finally{ui.post(()->{busy=false;if(alive)buttons();});}});}
    private void requireWifi()throws Exception{
        if(test)return;
        android.net.NetworkInfo n=((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if(n==null||!n.isConnected()||n.getType()!=ConnectivityManager.TYPE_WIFI)throw new IOException("Connect the head unit to Wi-Fi or a phone hotspot first. Wireless CarPlay may need to be disconnected.");
    }
    private void parse(String text)throws Exception{
        JSONObject root=new JSONObject(text);if(root.getInt("schemaVersion")!=1||!Policy.REPO.equals(root.getString("repository")))throw new IOException("Unsupported catalog or repository");
        JSONArray rows=root.getJSONArray("modules");if(rows.length()>50)throw new IOException("Catalog exceeds module limit");
        ArrayList<Module> parsed=new ArrayList<Module>();Set<String> ids=new HashSet<String>();
        for(int i=0;i<rows.length();i++){Module m=new Module(rows.getJSONObject(i),test);if(!ids.add(m.id))throw new IOException("Duplicate module ID");parsed.add(m);}
        ui.post(()->{if(!alive)return;modules.clear();modules.addAll(parsed);ArrayList<String> names=new ArrayList<String>();for(Module m:modules)names.add(m.name+"\n"+m.version+" • "+m.publisher);list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names));selected=modules.isEmpty()?null:modules.get(0);verified=null;describe("");buttons();});
    }
    private void loadCatalog(){job("Fetching test catalog…",()->{requireWifi();String json=Network.catalog(test,catalogUrl);parse(json);write(new File(getFilesDir(),"catalog.json"),json.getBytes("UTF-8"));log("Catalog refreshed from "+Policy.REPO);say("Catalog loaded. Select a module to review it.");});}
    private void describe(String note){if(selected==null){details.setText("No modules in the test catalog.");return;}
        details.setText(selected.name+"  "+selected.version+"\nPublisher: "+selected.publisher+"\nModule ID: "+selected.id+"\n\n"+selected.description+"\n\n"+selected.compatibility+"\n\nFirmware: "+(firmware.isEmpty()?"unknown":firmware)+"\n"+(matches()?"No declared firmware conflict; this is not a hardware validation.":"Firmware does not match the catalog declaration — install disabled.")+"\n\nDownload: "+selected.bytes+" bytes\nSHA-256: "+selected.hash+"\n\n"+note);
    }
    private void download(){final Module m=selected;if(m==null)return;verified=null;job("Downloading "+m.name+"…",()->{
        requireWifi();if(new StatFs(getFilesDir().getPath()).getAvailableBytes()<m.bytes+8*1024*1024)throw new IOException("Insufficient free internal storage");
        File dir=new File(getFilesDir(),"downloads");if(!dir.isDirectory()&&!dir.mkdir())throw new IOException("Cannot create download directory");
        File temp=new File(dir,UUID.randomUUID()+".part"),dest=new File(dir,m.id+"-"+m.hash.substring(0,16)+".zip");
        try{try(FileOutputStream out=new FileOutputStream(temp)){Network.fetch(m.url,out,Policy.MAX_DOWNLOAD,m.bytes,test,(n,total)->say("Downloading "+m.name+": "+n+" / "+total+" bytes"));out.getFD().sync();}
            say("Checking SHA-256 and module metadata…");PackageCheck.verify(temp,m.id,m.version,m.hash,m.bytes);
            if(dest.exists()&&!dest.delete())throw new IOException("Cannot replace prior download");if(!temp.renameTo(dest))throw new IOException("Cannot commit verified package");
            log("Verified "+m.id+" "+m.version+" SHA-256 "+m.hash);ui.post(()->{verified=dest;describe("Download verified against the catalog. Review the module before installing.");});say("Download verified. Ready for a deliberate install.");
        }finally{temp.delete();}
    });}
    private void confirmInstall(){final Module m=selected;final File file=verified;if(m==null||file==null)return;
        new AlertDialog.Builder(this).setTitle("Install "+m.name+" "+m.version+"?").setMessage("Publisher: "+m.publisher+"\n\n"+m.compatibility+"\n\nNEMESIS runs module installation scripts with root privileges. Install only while parked with ignition on. No automatic updates are enabled.")
            .setNegativeButton("Cancel",null).setPositiveButton("Install",(d,w)->install(m,file)).show();
    }
    private void install(final Module m,final File file){job("Checking package and parked state…",()->{
        if(pending.exists())throw new IOException("A prior installation has no confirmed outcome. Check installed status and NEMESIS Manager first.");
        if(!m.matches(prop("ro.build.product2")))throw new IOException("Firmware declaration no longer matches");
        PackageCheck.verify(file,m.id,m.version,m.hash,m.bytes);
        Cli.Result gate=Cli.run(test,15000,"root","-c","/system/bin/sh /data/nemesis/apps/module.browser/bin/check-park.sh");
        log("Park check: "+gate.output);if(gate.code!=0||!gate.output.trim().endsWith("CATALOG_PARK_OK"))throw new IOException("Park/ignition check not confirmed. "+gate.output);
        if(!pending.createNewFile())throw new IOException("Another installation is pending");
        JSONObject marker=new JSONObject();marker.put("id",m.id);marker.put("version",m.version);write(pending,marker.toString().getBytes("UTF-8"));
        say("Installing "+m.name+". Keep the head unit powered on…");
        Cli.Result result=Cli.run(test,180000,"install","--update",file.getAbsolutePath());log("Install "+m.id+" exit "+result.code+"\n"+result.output);
        if(result.code==-999)throw new IOException("Installation outcome is not confirmed. Do not retry; check NEMESIS Manager.\n"+result.output);
        if(result.code!=0){pending.delete();throw new IOException("NEMESIS reported an installation failure.\n"+result.output);}
        String current=installedVersion(m.id);if(!m.version.equals(current))throw new IOException("NEMESIS returned success, but the installed version could not be confirmed. Check NEMESIS Manager before retrying.");
        pending.delete();say("Installed "+m.name+" "+current);ui.post(()->{describe("Installed version confirmed: "+current);show("Installation complete",m.name+" "+current+" was installed.\n\n"+result.output);});
    });}
    private String installedVersion(String id){try{Policy.id(id);File f=test?new File(getFilesDir(),"test-installed/"+id+".data"):new File("/data/nemesis/apps/"+id+"/NEMESIS.data");return PackageCheck.descriptor(read(f)).get("version");}catch(Exception e){return null;}}
    private void checkInstalled(){job("Checking NEMESIS packages…",()->{
        Cli.Result r=Cli.run(test,10000,"pkg-list");log("Package list exit "+r.code+"\n"+r.output);if(r.code!=0)throw new IOException("NEMESIS package list unavailable.\n"+r.output);
        if(pending.exists()){try{JSONObject p=new JSONObject(read(pending));if(p.getString("version").equals(installedVersion(p.getString("id"))))pending.delete();}catch(Exception ignored){}}
        final String version=selected==null?null:installedVersion(selected.id);ui.post(()->{describe("Installed version: "+(version==null?"not confirmed":version));show("Installed modules",r.output+(pending.exists()?"\nA previous install still has no confirmed outcome.":""));});say("Installed-module status refreshed.");
    });}
    static String read(File f)throws IOException{if(f.length()>1024*1024)throw new IOException("File too large");try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IOException("File too large");}return out.toString("UTF-8");}}
    static void write(File f,byte[] b)throws IOException{try(FileOutputStream out=new FileOutputStream(f)){out.write(b);out.getFD().sync();}}
    protected void onDestroy(){alive=false;jobs.shutdown();super.onDestroy();}
}
