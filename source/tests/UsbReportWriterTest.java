import dev.cameraprobe.UsbReportWriter;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
public class UsbReportWriterTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[]args)throws Exception{
        Path temp=java.nio.file.Files.createTempDirectory("camera-probe-usb-test");
        File usb=temp.resolve("usb0").toFile();usb.mkdir();byte[] data="{\"camera\":0,\"label\":\"rear\"}".getBytes("UTF-8");
        File[] roots={usb};String mounts="test "+usb.getPath()+" fuse rw 0 0\n";
        try{
            try{UsbReportWriter.write(roots,"","one.json",data);throw new AssertionError("Unmounted directory accepted");}catch(IOException expected){}
            check(!new File(usb,"CameraFeedProbe").exists(),"No write into an unmounted USB root");
            File saved=UsbReportWriter.write(roots,mounts,"one.json",data);
            check(Arrays.equals(data,java.nio.file.Files.readAllBytes(saved.toPath())),"JSON bytes written intact");
            check(!new File(saved.getPath()+".tmp").exists(),"No temporary file after commit");
            try{UsbReportWriter.write(roots,mounts,"one.json",data);throw new AssertionError("Existing report overwritten");}catch(IOException expected){}
            try{UsbReportWriter.write(roots,mounts,"../escape.json",data);throw new AssertionError("Path traversal accepted");}catch(IOException expected){}
            File absent=temp.resolve("absent").toFile();
            try{UsbReportWriter.write(new File[]{absent},mounts,"two.json",data);throw new AssertionError("Missing root accepted");}catch(IOException expected){}
            check(!absent.exists(),"Missing USB root must not be created");
            File usb1=temp.resolve("usb1").toFile();usb1.mkdir();String second="test "+usb1.getPath()+" fuse rw 0 0\n";
            check(UsbReportWriter.write(new File[]{usb,usb1},second,"second.json",data).getParentFile().getParentFile().equals(usb1),"Select mounted secondary USB");
            System.out.println("PASS: USB report bytes, atomic commit, absent/unmounted roots, duplicate names, path validation, secondary USB");
        }finally{java.nio.file.Files.walk(temp).sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{java.nio.file.Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
    }
}
