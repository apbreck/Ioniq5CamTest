package dev.nemesiscatalog;

import android.os.IBinder;
/** Fixed, read-only vehicle-state check. Called via the separately installed NEMESIS root channel. */
public final class InstallGate {
    static Object service(String name,String type)throws Exception{
        Object b=Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
        if(b==null)throw new IllegalStateException("Missing vehicle-state service");
        return Class.forName(type+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
    }
    public static void main(String[]args){
        try{
            Object os=Class.forName("libcore.io.Libcore").getField("os").get(null);
            if((Integer)os.getClass().getMethod("getuid").invoke(os)==0){os.getClass().getMethod("setgid",int.class).invoke(os,1000);os.getClass().getMethod("setuid",int.class).invoke(os,1000);}
            if(android.os.Process.myUid()!=1000)throw new SecurityException("Wrong gate identity");
            String reg="com.hkmc.regulation.IRegulationManager",bcm="com.hkmc.bcm.IBCMService";
            int status=(Integer)Class.forName(reg).getMethod("getStatus").invoke(service("com.hkmc.regulation",reg));
            boolean ignition=(Boolean)Class.forName(bcm).getMethod("getIGNStatus").invoke(service("com.hkmc.micom.bcm",bcm));
            if(status!=0||!ignition)throw new IllegalStateException("Keep the vehicle parked with ignition on");
            System.out.println("CATALOG_PARK_OK");
        }catch(Throwable t){System.out.println("CATALOG_PARK_BLOCKED: "+t);System.exit(1);}
    }
}
