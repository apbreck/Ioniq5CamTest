package dev.cameraprobe;

import java.io.*;

/** Only writes into an already-mounted USB root; never creates a substitute mount directory. */
public final class UsbReportWriter {
    private UsbReportWriter() {}
    public static boolean mounted(File root,String mountTable) throws IOException {
        String wanted=root.getCanonicalPath();
        for(String line:mountTable.split("\n")){
            String[] fields=line.trim().split("\\s+");
            if(fields.length<4)continue;
            String path=fields[1].replace("\\040"," ").replace("\\011","\t").replace("\\134","\\");
            if(path.equals(root.getPath())||path.equals(wanted))return true;
            try{if(new File(path).getCanonicalPath().equals(wanted))return true;}catch(IOException ignored){}
        }
        return false;
    }
    public static File write(File[] roots,String mounts,String name,byte[] report) throws IOException {
        if(!name.matches("[A-Za-z0-9._-]+\\.json"))throw new IOException("Invalid report filename");
        if(report.length>1024*1024)throw new IOException("Report exceeds 1 MiB");
        File root=null;
        for(File candidate:roots)if(candidate.isDirectory()&&candidate.canWrite()&&mounted(candidate,mounts)){root=candidate;break;}
        if(root==null)throw new IOException("No writable USB drive found. Insert a USB drive and tap Export report again.");
        File folder=new File(root,"CameraFeedProbe");
        if(!folder.isDirectory()&&!folder.mkdir())throw new IOException("Cannot create CameraFeedProbe folder on USB drive");
        if(!folder.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw new IOException("USB report folder must not be a link outside the drive");
        File dest=new File(folder,name),temp=new File(folder,name+".tmp");
        if(dest.exists()||!temp.createNewFile())throw new IOException("Report filename already exists; please export again");
        boolean committed=false;
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(report);out.getFD().sync();}
            if(!temp.renameTo(dest))throw new IOException("Could not finish the report on USB drive");
            committed=true;return dest;
        }finally{if(!committed)temp.delete();}
    }
}
