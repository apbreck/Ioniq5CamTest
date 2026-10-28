package dev.cameraprobe;

import java.io.*;
import org.json.JSONObject;

final class Files {
    static final File ROOT=new File("/data/nemesis/camera-probe");
    static final String[] APIS={"android.hardware.Camera","android.hardware.RearCamera"};
    static String read(File f) throws IOException {
        if (f.length()>1024*1024) throw new IOException("File exceeds read limit");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try (InputStream in=new FileInputStream(f)) {
            byte[] b=new byte[4096];int n;
            while ((n=in.read(b))!=-1) {out.write(b,0,n);if(out.size()>1024*1024) throw new IOException("Read limit");}
        }
        return new String(out.toByteArray(),"UTF-8");
    }
    static JSONObject json(String name) throws Exception {return new JSONObject(read(new File(ROOT,name)));}
    static void write(File f,byte[] b) throws IOException {
        File t=new File(f.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(t)){out.write(b);}
        // Exchanged files are deliberately writable by the UI UID and the isolated system helper.
        t.setReadable(true,false);t.setWritable(true,false);
        if (!t.renameTo(f)) throw new IOException("Cannot commit "+f);
    }
    static void write(String name,JSONObject v) throws IOException {write(new File(ROOT,name),v.toString().getBytes("UTF-8"));}
    static String error(Throwable t) {
        while(t.getCause()!=null && t.getCause()!=t)t=t.getCause();
        return t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage());
    }
}
