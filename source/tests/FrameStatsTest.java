import dev.cameraprobe.FrameStats;
import java.util.Arrays;
public class FrameStatsTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[]args){
        byte[] b=new byte[64*48*2];
        check(FrameStats.analyze(b,64,48,20).placeholder,"untouched zero buffer");
        Arrays.fill(b,(byte)128);check(FrameStats.analyze(b,64,48,20).placeholder,"flat grey");
        for(int i=0;i<b.length;i+=2){b[i]=(byte)((i/2)%240);b[i+1]=(byte)128;}
        FrameStats s=FrameStats.analyze(b,64,48,20);check(!s.placeholder&&s.max-s.min>100,"YUY2 detail");
        long hash=s.fingerprint;b[0]=17;check(FrameStats.analyze(b,64,48,20).fingerprint!=hash,"changed sample");
        Arrays.fill(b,(byte)0);for(int i=1;i<b.length;i+=2)b[i]=(byte)(i%255);
        check(FrameStats.analyze(b,64,48,20).placeholder,"chroma noise must not masquerade as luma detail");
        try{FrameStats.analyze(new byte[8],640,480,17);throw new AssertionError("short buffer accepted");}catch(IllegalArgumentException expected){}
        try{FrameStats.analyze(b,64,48,1);throw new AssertionError("RGB guessed as YUV");}catch(IllegalArgumentException expected){}
        byte[] padded=new byte[80*48*2];for(int y=0;y<48;y++)Arrays.fill(padded,y*80,y*80+66,(byte)128);
        check(FrameStats.analyze(padded,66,48,842094169).placeholder,"YV12 aligned stride");
        byte[] yv=new byte[80*48+48*24*2];
        Arrays.fill(yv,0,80*48,(byte)90);Arrays.fill(yv,80*48,80*48+48*24,(byte)111);Arrays.fill(yv,80*48+48*24,yv.length,(byte)145);
        byte[] nv=FrameStats.yv12ToNv21(yv,66,48);
        check(nv.length==66*48*3/2&&nv[66*48]==111&&(nv[66*48+1]&255)==145,"YV12 planes, padding and VU order");
        System.out.println("PASS: zero/grey placeholders, YUY2 luma, changing frame, chroma-only noise, short/unknown buffers, aligned YV12");
    }
}
