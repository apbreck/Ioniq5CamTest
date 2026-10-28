package dev.nemesiscatalog;

import java.net.URL;
public final class Policy {
    public static final String REPO="apbreck/Ioniq5CamTest";
    public static final String CATALOG="https://raw.githubusercontent.com/"+REPO+"/main/module-catalog.json";
    public static final long MAX_DOWNLOAD=64L*1024*1024;
    public static void id(String value){if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")||value.contains(".."))throw new IllegalArgumentException("Invalid module ID");}
    public static void version(String value){if(value==null||!value.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}"))throw new IllegalArgumentException("Invalid version");}
    public static void asset(String value,boolean test)throws Exception{
        URL u=new URL(value);
        if(test&&local(u))return;
        if(!"https".equals(u.getProtocol())||!"github.com".equals(u.getHost())||u.getPort()!=-1||u.getUserInfo()!=null||u.getQuery()!=null||u.getRef()!=null||!u.getPath().matches("/apbreck/Ioniq5CamTest/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.zip"))throw new IllegalArgumentException("Package must be a release ZIP from "+REPO);
    }
    public static boolean local(URL u){return "http".equals(u.getProtocol())&&("10.0.2.2".equals(u.getHost())||"127.0.0.1".equals(u.getHost()))&&u.getUserInfo()==null;}
    public static void transport(URL u,boolean test)throws Exception{
        if(test&&local(u))return;
        String h=u.getHost();
        if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||!(h.equals("github.com")||h.equals("raw.githubusercontent.com")||h.equals("release-assets.githubusercontent.com")||h.equals("objects.githubusercontent.com")))throw new IllegalArgumentException("Unapproved download destination");
    }
}
