package dev.nemesiscatalog;

import java.io.*;
final class Cli {
    static final class Result{final int code;final String output;Result(int c,String o){code=c;output=o;}}
    static Result run(boolean test,long timeout,String...args)throws Exception{
        String[] command=new String[args.length+1];command[0]=test?"/data/local/tmp/nemesis-catalog-fixture":"/system/xbin/nemesis";System.arraycopy(args,0,command,1,args.length);
        final Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        final StringBuilder log=new StringBuilder();
        Thread reader=new Thread(new Runnable(){public void run(){try(Reader r=new InputStreamReader(process.getInputStream(),"UTF-8")){char[] b=new char[2048];int n;while((n=r.read(b))!=-1)synchronized(log){if(log.length()<65536)log.append(b,0,Math.min(n,65536-log.length()));}}catch(Exception ignored){}}},"nemesis-command-output");reader.start();
        long until=System.currentTimeMillis()+timeout;
        for(;;){try{int code=process.exitValue();reader.join(1000);synchronized(log){return new Result(code,log.toString());}}catch(IllegalThreadStateException running){}
            if(System.currentTimeMillis()>until){if(!"install".equals(args[0]))process.destroy();synchronized(log){return new Result(-999,log+"\nNo completion confirmation before timeout. Installation may still be running; do not retry automatically.");}}
            Thread.sleep(100);
        }
    }
}
