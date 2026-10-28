package dev.nemesiscatalog;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
public final class PackageCheck {
    public static String sha256(File file)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=new byte[16384];
        try(InputStream in=new FileInputStream(file)){int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder out=new StringBuilder();for(byte v:d.digest())out.append(String.format(Locale.US,"%02x",v&255));return out.toString();
    }
    public static Map<String,String> descriptor(String text){
        Map<String,String> result=new HashMap<String,String>();boolean section=false;
        for(String line:text.split("\n")){line=line.trim();if(line.startsWith("#")||line.startsWith(";")||line.isEmpty())continue;
            if(line.startsWith("[")){section=line.equals("[nemesis]");continue;}
            int eq=line.indexOf('=');if(section&&eq>0){String key=line.substring(0,eq).trim();if(result.containsKey(key))throw new IllegalArgumentException("Duplicate package metadata key");result.put(key,line.substring(eq+1).trim());}}
        return result;
    }
    public static void verify(File file,String id,String version,String hash,long size)throws Exception{
        Policy.id(id);Policy.version(version);
        if(!hash.matches("[0-9a-f]{64}")||file.length()!=size||size<=0||size>Policy.MAX_DOWNLOAD||!sha256(file).equals(hash))throw new IOException("Download size or SHA-256 does not match the catalog");
        String metadata=null;Set<String> paths=new HashSet<String>();long expanded=0;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(new FileInputStream(file))){ZipEntry e;byte[] b=new byte[16384];
            while((e=zip.getNextEntry())!=null){String name=e.getName();
                if(++entries>4096||name.startsWith("/")||name.contains("\\")||name.contains(":")||Arrays.asList(name.split("/")).contains("..")||!paths.add(name))throw new IOException("Invalid or duplicate ZIP path");
                ByteArrayOutputStream desc="NEMESIS.data".equals(name)?new ByteArrayOutputStream():null;int n;
                while((n=zip.read(b))!=-1){expanded+=n;if(expanded>128L*1024*1024)throw new IOException("Package exceeds extraction limit");if(desc!=null){if(desc.size()+n>16384)throw new IOException("Package descriptor too large");desc.write(b,0,n);}}
                if(desc!=null)metadata=new String(desc.toByteArray(),"UTF-8");zip.closeEntry();}
        }
        if(metadata==null)throw new IOException("Missing root NEMESIS.data; use the module ZIP, not its USB wrapper");
        Map<String,String> m=descriptor(metadata);
        if(!id.equals(m.get("id"))||!version.equals(m.get("version")))throw new IOException("Package ID/version differs from the catalog");
        if("install".equals(m.get("type")))throw new IOException("USB installer wrapper is not a module");
    }
}
