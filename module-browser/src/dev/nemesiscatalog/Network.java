package dev.nemesiscatalog;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
final class Network {
    interface Progress{void update(long received,long expected);}
    private static final class Tls extends SSLSocketFactory {
        final SSLSocketFactory delegate=(SSLSocketFactory)SSLSocketFactory.getDefault();
        Socket configure(Socket s){if(s instanceof SSLSocket){SSLSocket ssl=(SSLSocket)s;List<String> p=new ArrayList<String>();for(String v:ssl.getSupportedProtocols())if(v.equals("TLSv1.2")||v.equals("TLSv1.3"))p.add(v);ssl.setEnabledProtocols(p.toArray(new String[0]));}return s;}
        public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
        public Socket createSocket(Socket s,String h,int p,boolean c)throws IOException{return configure(delegate.createSocket(s,h,p,c));}
        public Socket createSocket(String h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
        public Socket createSocket(String h,int p,InetAddress a,int b)throws IOException{return configure(delegate.createSocket(h,p,a,b));}
        public Socket createSocket(InetAddress h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
        public Socket createSocket(InetAddress h,int p,InetAddress a,int b)throws IOException{return configure(delegate.createSocket(h,p,a,b));}
    }
    static void fetch(String source,OutputStream out,long limit,long expected,boolean test,Progress progress)throws Exception{
        URL url=new URL(source);
        for(int redirects=0;redirects<6;redirects++){
            Policy.transport(url,test);HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","NemesisModuleBrowser/0.1.0");c.setRequestProperty("Accept-Encoding","identity");
            if(c instanceof HttpsURLConnection)((HttpsURLConnection)c).setSSLSocketFactory(new Tls());
            try{int code=c.getResponseCode();if(code==301||code==302||code==303||code==307||code==308){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("Redirect without destination");url=new URL(url,loc);continue;}
                if(code!=200)throw new IOException("HTTP "+code+" from "+url.getHost());
                int length=c.getContentLength();if(length>limit)throw new IOException("Download exceeds limit");
                byte[] b=new byte[16384];long received=0,last=0;
                try(InputStream in=c.getInputStream()){int n;while((n=in.read(b))!=-1){received+=n;if(received>limit)throw new IOException("Download exceeds limit");out.write(b,0,n);long now=System.currentTimeMillis();if(progress!=null&&now-last>500){progress.update(received,expected);last=now;}}}
                if(expected>=0&&received!=expected)throw new IOException("Incomplete download");return;
            }finally{c.disconnect();}
        }throw new IOException("Too many redirects");
    }
    static String catalog(boolean test,String url)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();fetch(url,out,512*1024,-1,test,null);return out.toString("UTF-8");}
}
