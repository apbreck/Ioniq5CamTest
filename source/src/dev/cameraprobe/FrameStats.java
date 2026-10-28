package dev.cameraprobe;

/** Evidence only: image detail and changes cannot establish which physical camera is connected. */
public final class FrameStats {
    public final int min, max, samples;
    public final double mean, variance;
    public final long fingerprint;
    public final boolean placeholder;
    private FrameStats(int lo, int hi, int n, double avg, double var, long hash, boolean flat) {
        min=lo; max=hi; samples=n; mean=avg; variance=var; fingerprint=hash; placeholder=flat;
    }
    public static FrameStats analyze(byte[] data, int width, int height, int format) {
        if (width<=0 || height<=0 || data==null) throw new IllegalArgumentException("Invalid frame");
        // Android NV21/YV12: Y plane first; YUY2: Y0 U Y1 V. BGRA is not guessed as YUV.
        if (format!=17 && format!=20 && format!=842094169) throw new IllegalArgumentException("Unsupported pixel format "+format);
        int stride = format==20 ? width*2 : format==842094169 ? (width+15)/16*16 : width;
        long required=(long)stride*height;
        if (required>data.length) throw new IllegalArgumentException("Short frame: "+data.length+" < "+required);
        int lo=255,hi=0,n=0; long sum=0,sq=0,hash=1469598103934665603L;
        boolean zeros=true, greys=true;
        for (int y=0;y<height;y+=Math.max(1,height/24)) for (int x=0;x<width;x+=Math.max(1,width/32)) {
            int v=data[y*stride+(format==20 ? 2*x:x)]&255;
            lo=Math.min(lo,v); hi=Math.max(hi,v);sum+=v;sq+=(long)v*v;n++;
            hash=(hash^v)*1099511628211L; zeros &= v==0; greys &= v==128;
        }
        double mean=(double)sum/n;
        return new FrameStats(lo,hi,n,mean,(double)sq/n-mean*mean,hash,zeros||greys);
    }
    public String verdict() {
        if (placeholder) return "Uniform 0/128 buffer: placeholder or flat scene; video unconfirmed";
        if (max-min<8) return "Frames arriving; dark/flat image; scene unconfirmed";
        return "Image detail detected; confirm the scene visually";
    }
    public static byte[] yv12ToNv21(byte[] data,int width,int height){
        if(width<=0||height<=0||(width&1)!=0||(height&1)!=0)throw new IllegalArgumentException("Even YV12 dimensions required");
        int ys=(width+15)/16*16,cs=(ys/2+15)/16*16;
        if((long)ys*height+(long)cs*height>data.length)throw new IllegalArgumentException("Short YV12 frame");
        byte[] out=new byte[width*height*3/2];
        for(int y=0;y<height;y++)System.arraycopy(data,y*ys,out,y*width,width);
        int vstart=ys*height,ustart=vstart+cs*(height/2),dest=width*height;
        for(int y=0;y<height/2;y++)for(int x=0;x<width/2;x++){out[dest++]=data[vstart+y*cs+x];out[dest++]=data[ustart+y*cs+x];}
        return out;
    }
}
