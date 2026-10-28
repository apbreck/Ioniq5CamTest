package dev.cameraprobe;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
    private final Handler timer=new Handler();
    private final ArrayList<JSONObject> feeds=new ArrayList<JSONObject>();
    private Spinner feed,size,verdict;
    private TextView status,identity,evidence;
    private TextView interfaceNotes;
    private ImageView image;
    private EditText label,notes;
    private Button start;
    private Button export;
    private String exportId="";
    private long exportStarted;
    private boolean resumed=false,requested=false;
    private String session="",requestApi="",requestSize="auto",selection="";
    private int requestId=-1;
    private long lastImage=0;
    private Bitmap bitmap;
    private JSONObject lastStatus=new JSONObject();
    private SharedPreferences saved;
    private final int foreground=Color.rgb(233,238,246),subtle=Color.rgb(166,181,199),bg=Color.rgb(15,23,35),panel=Color.rgb(25,38,54);
    public void onCreate(Bundle b){
        super.onCreate(b);saved=getSharedPreferences("labels",0);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setFocusableInTouchMode(true);root.setPadding(20,12,20,12);root.setBackgroundColor(bg);setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        TextView title=text("Camera Feed Probe",25,foreground);top.addView(title,new LinearLayout.LayoutParams(0,52,1));
        export=button("Export report");top.addView(export);export.setOnClickListener(new View.OnClickListener(){public void onClick(View v){exportReport();}});
        TextView hint=text("Diagnostic inventory • vehicle preview disabled • USB report export",15,subtle);root.addView(hint);
        LinearLayout body=new LinearLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView controls=new ScrollView(this);body.addView(controls,new LinearLayout.LayoutParams(430,-1));
        LinearLayout left=new LinearLayout(this);left.setPadding(0,12,20,0);left.setOrientation(1);controls.addView(left);
        left.addView(text("Feed interface / ID",16,subtle));feed=new Spinner(this);left.addView(feed);
        interfaceNotes=text("",14,Color.rgb(245,190,100));left.addView(interfaceNotes);
        feed.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){selectionChanged();}});
        left.addView(text("Preview size",16,subtle));size=new Spinner(this);size.setAdapter(adapter(new String[]{"auto","640x480","720x480","1280x720"}));left.addView(size);
        LinearLayout buttons=new LinearLayout(this);left.addView(buttons);start=button("Start test");buttons.addView(start,new LinearLayout.LayoutParams(0,64,1));
        Button stop=button("Stop");buttons.addView(stop,new LinearLayout.LayoutParams(0,64,1));
        start.setOnClickListener(new View.OnClickListener(){public void onClick(View v){begin();}});stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){end();}});
        left.addView(text("Identify the picture yourself",18,foreground));
        label=new EditText(this);label.setSingleLine(true);label.setHint("e.g. rear bumper / stitched 360");label.setTextSize(17);left.addView(label);
        verdict=new Spinner(this);verdict.setAdapter(adapter(new String[]{"Unconfirmed","Visible scene confirmed","No visible scene","Wrong colors / distorted"}));left.addView(verdict);
        notes=new EditText(this);notes.setHint("Scene, obstructions, colors, or other notes");notes.setMinLines(2);notes.setTextSize(16);left.addView(notes);
        Button save=button("Save identification");left.addView(save);save.setOnClickListener(new View.OnClickListener(){public void onClick(View v){saveLabel();}});
        Button refresh=button("Refresh feed list");left.addView(refresh);refresh.setOnClickListener(new View.OnClickListener(){public void onClick(View v){loadFeeds();}});
        TextView explanation=text("FRONT/BACK is an Android metadata hint, not a physical-camera name. Different IDs can show the same source. No callback frames does not prove the camera has no video.",15,subtle);left.addView(explanation);
        LinearLayout right=new LinearLayout(this);right.setOrientation(1);right.setBackgroundColor(panel);right.setPadding(16,12,16,12);body.addView(right,new LinearLayout.LayoutParams(0,-1,1));
        identity=text("Select a feed",19,foreground);right.addView(identity);
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackgroundColor(Color.BLACK);right.addView(image,new LinearLayout.LayoutParams(-1,0,1));
        evidence=text("No preview requested",17,foreground);right.addView(evidence);
        status=text("Waiting for the Nemesis helper…",15,subtle);right.addView(status);
        loadFeeds();
    }
    private TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setPadding(4,5,4,5);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextSize(16);return b;}
    private ArrayAdapter<String> adapter(String[] a){ArrayAdapter<String> r=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,a);r.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);return r;}
    private String key(JSONObject f){return f.optString("api")+"#"+f.optInt("id");}
    private JSONObject selected(){int i=feed.getSelectedItemPosition();return i>=0&&i<feeds.size()?feeds.get(i):null;}
    private void loadFeeds(){
        if(requested)end();feeds.clear();ArrayList<String> names=new ArrayList<String>();
        try{StringBuilder errors=new StringBuilder();JSONArray a=Files.json("catalogue.json").getJSONArray("feeds");for(int i=0;i<a.length();i++){JSONObject f=a.getJSONObject(i);if(!f.has("id")){errors.append(f.optString("api")).append(": ").append(f.optString("error")).append("\n");continue;}feeds.add(f);String name=f.getString("api").endsWith("RearCamera")?"RearCamera":"Camera";names.add(name+" #"+f.getInt("id")+" • metadata "+(f.optInt("facing",-1)==0?"BACK":f.optInt("facing",-1)==1?"FRONT":"unknown"));}interfaceNotes.setText(errors.toString());}
        catch(Exception e){status.setText("Helper catalogue unavailable. Install/start the Nemesis package first.");}
        feed.setAdapter(adapter(names.toArray(new String[0])));start.setEnabled(false);
    }
    private void selectionChanged(){
        JSONObject f=selected();if(f==null)return;String k=key(f);if(k.equals(selection))return;
        if(requested)end();selection=k;label.setText(saved.getString(k+".label",""));notes.setText(saved.getString(k+".notes",""));verdict.setSelection(saved.getInt(k+".verdict",0));
        identity.setText(f.optString("api")+" • ID "+f.optInt("id")+" • orientation "+f.optInt("orientation",0)+"°");
        clearImage();evidence.setText(lastStatus.optBoolean("captureEnabled")?"Untested selection — press Start":"Inventory only — camera connection and video are unverified");
    }
    private void begin(){
        if(!lastStatus.optBoolean("captureEnabled",false)){evidence.setText("Preview disabled on vehicle hardware; export the inventory report instead.");return;}
        JSONObject f=selected();if(f==null)return;
        requestApi=f.optString("api");requestId=f.optInt("id");requestSize=String.valueOf(size.getSelectedItem());
        session=UUID.randomUUID().toString();requested=true;clearImage();evidence.setText("Requesting preview…");writeRequest();
    }
    private void end(){requested=false;writeRequest();clearImage();}
    private void clearImage(){lastImage=0;image.setImageDrawable(null);bitmap=null;}
    private void writeRequest(){
        try{JSONObject q=new JSONObject();q.put("action",requested?"start":"stop");q.put("session",session);q.put("api",requestApi);q.put("id",requestId);q.put("size",requestSize);q.put("renewed",SystemClock.elapsedRealtime());Files.write("request.json",q);}
        catch(Exception e){requested=false;status.setText("Cannot reach helper: "+Files.error(e));}
    }
    private final Runnable poll=new Runnable(){public void run(){
        if(!resumed)return;if(requested)writeRequest();
        checkExportResult();
        if(feeds.isEmpty()&&new File(Files.ROOT,"catalogue.json").canRead())loadFeeds();
        try{
            JSONObject s=Files.json("status.json");lastStatus=s;
            long age=SystemClock.elapsedRealtime()-s.getLong("updated");
            if(age<0||age>5000){status.setText("Helper not responding. Preview lease will expire.");if(requested)end();}
            else {
                boolean enabled=s.optBoolean("captureEnabled",false);start.setEnabled(enabled&&!feeds.isEmpty());start.setText(enabled?"Start test":"Preview disabled");
                if(!enabled)evidence.setText("Inventory only — no camera open attempted; video is not tested.");
                status.setText("Stage: "+s.optString("phase")+" • Open confirmed: "+s.optBoolean("openConfirmed")+" • Preview start confirmed: "+s.optBoolean("previewStartConfirmed")+"\n"+s.optString("reason")+(s.optBoolean("active")?" • "+s.optInt("secondsLeft")+"s left":""));
                JSONObject f=selected();boolean match=f!=null&&f.optString("api").equals(s.optString("api"))&&f.optInt("id")==s.optInt("id");
                if(match){
                    evidence.setText(enabled?s.optString("evidence")+String.format(Locale.US,"\n%d callback frames • %.1f fps • %d sample changes • %dx%d • format %d",s.optLong("frames"),s.optDouble("fps"),s.optLong("sampleChanges"),s.optInt("width"),s.optInt("height"),s.optInt("format"))+"\n"+s.optString("previewNote"):"Inventory only — no camera open attempted; video is not tested.");
                    if(requested&&session.equals(s.optString("session"))&&!s.optBoolean("active")&&!s.optBoolean("opening")){requested=false;writeRequest();}
                    long frameAge=s.optLong("lastFrameAgeMs",-1);
                    if(s.optBoolean("active")&&frameAge>=0&&frameAge<1500){
                        File jpg=new File(Files.ROOT,"preview.jpg");long m=jpg.lastModified();
                        if(m!=0&&m!=lastImage){Bitmap next=BitmapFactory.decodeFile(jpg.getPath());if(next!=null){bitmap=next;image.setImageBitmap(next);lastImage=m;}}
                    }else if(bitmap!=null){clearImage();}
                }
            }
        }catch(Exception e){status.setText("Helper unavailable — no camera test running");}
        timer.postDelayed(this,500);
    }};
    private void saveLabel(){
        JSONObject f=selected();if(f==null)return;String k=key(f);
        saved.edit().putString(k+".label",label.getText().toString()).putString(k+".notes",notes.getText().toString()).putInt(k+".verdict",verdict.getSelectedItemPosition()).commit();
        Toast.makeText(this,"Identification saved for "+k,Toast.LENGTH_SHORT).show();
    }
    private void exportReport(){
        try{
            end();
            JSONObject out=new JSONObject();out.put("createdAt",System.currentTimeMillis());out.put("catalogue",Files.json("catalogue.json"));out.put("lastStatus",lastStatus);
            String[] verdicts={"Unconfirmed","Visible scene confirmed","No visible scene","Wrong colors / distorted"};
            JSONArray entries=new JSONArray();for(JSONObject f:feeds){String k=key(f);JSONObject r=new JSONObject(f.toString());r.put("userLabel",saved.getString(k+".label",""));r.put("userNotes",saved.getString(k+".notes",""));int v=saved.getInt(k+".verdict",0);r.put("userVerdict",verdicts[Math.max(0,Math.min(3,v))]);entries.put(r);}out.put("identifications",entries);
            try{out.put("sessionHistoryJsonl",Files.read(new File(Files.ROOT,"sessions.jsonl")));}catch(Exception ignored){}
            Files.write("report.json",out);
            exportId=UUID.randomUUID().toString();exportStarted=SystemClock.elapsedRealtime();
            JSONObject request=new JSONObject();request.put("id",exportId);request.put("created",exportStarted);request.put("report",out);
            Files.write("export-request.json",request);export.setEnabled(false);export.setText("Saving to USB…");
        }catch(Exception e){exportId="";export.setEnabled(true);export.setText("Export report");new AlertDialog.Builder(this).setMessage("Export failed: "+Files.error(e)).setPositiveButton("OK",null).show();}
    }
    private void checkExportResult(){
        if(exportId.isEmpty())return;
        try{
            JSONObject result=Files.json("export-result.json");
            if(exportId.equals(result.optString("id"))){
                exportId="";export.setEnabled(true);export.setText("Export report");
                boolean ok=result.optBoolean("success");
                new AlertDialog.Builder(this).setTitle(ok?"Report saved to USB":"USB export failed")
                    .setMessage(ok?"Saved on your USB drive:\n"+result.optString("path")+"\n\nNo camera images are included. Wait for USB activity to finish before removing the drive.":result.optString("error"))
                    .setPositiveButton("OK",null).show();return;
            }
        }catch(Exception ignored){}
        if(SystemClock.elapsedRealtime()-exportStarted>30000){
            exportId="";export.setEnabled(true);export.setText("Export report");
            new AlertDialog.Builder(this).setTitle("USB export not confirmed").setMessage("The helper has not confirmed a saved report. Check the USB drive before retrying.").setPositiveButton("OK",null).show();
        }
    }
    protected void onResume(){super.onResume();resumed=true;timer.post(poll);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    protected void onPause(){resumed=false;timer.removeCallbacks(poll);end();super.onPause();}
    protected void onDestroy(){clearImage();super.onDestroy();}
}
