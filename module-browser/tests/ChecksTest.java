import dev.nemesiscatalog.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
public final class ChecksTest {
    interface Attempt{void run()throws Exception;}
    static void rejected(Attempt action)throws Exception{try{action.run();throw new AssertionError("Unsafe input accepted");}catch(IllegalArgumentException|IOException expected){}}
    static File zip(Path dir,String name,String descriptor,String extra)throws Exception{
        File f=dir.resolve(name).toFile();try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f))){z.putNextEntry(new ZipEntry("NEMESIS.data"));z.write(descriptor.getBytes("UTF-8"));z.closeEntry();if(extra!=null){z.putNextEntry(new ZipEntry(extra));z.write(1);z.closeEntry();}}return f;
    }
    public static void main(String[]args)throws Exception{
        Path dir=Files.createTempDirectory("nemesis-catalog-test");
        try{
            Policy.asset("https://github.com/apbreck/Ioniq5CamTest/releases/download/test-1/module.zip",false);
            rejected(()->Policy.asset("https://github.com/other/repo/releases/download/v1/module.zip",false));
            rejected(()->Policy.asset("http://github.com/apbreck/Ioniq5CamTest/releases/download/v1/module.zip",false));
            rejected(()->Policy.asset("https://github.com/apbreck/Ioniq5CamTest/releases/download/v1/module.zip?override=1",false));
            rejected(()->Policy.transport(new java.net.URL("https://evil.example/payload.zip"),false));
            rejected(()->Policy.transport(new java.net.URL("http://10.0.2.2:8786/module.zip"),false));
            Policy.transport(new java.net.URL("http://10.0.2.2:8786/module.zip"),true);
            File good=zip(dir,"good.zip","[nemesis]\nid=catalog.hello\nversion=1.0.0\n",null);String h=PackageCheck.sha256(good);
            PackageCheck.verify(good,"catalog.hello","1.0.0",h,good.length());
            rejected(()->PackageCheck.verify(good,"catalog.hello","1.0.0",h,good.length()+1));
            rejected(()->PackageCheck.verify(good,"catalog.hello","1.0.0",new String(new char[64]).replace('\0','0'),good.length()));
            rejected(()->PackageCheck.verify(good,"catalog.other","1.0.0",h,good.length()));
            File traverse=zip(dir,"traverse.zip","[nemesis]\nid=catalog.hello\nversion=1.0.0\n","../escape");
            rejected(()->PackageCheck.verify(traverse,"catalog.hello","1.0.0",PackageCheck.sha256(traverse),traverse.length()));
            File wrapper=zip(dir,"wrapper.zip","[nemesis]\ntype=install\npath=packages/module.zip\n",null);
            rejected(()->PackageCheck.verify(wrapper,"catalog.hello","1.0.0",PackageCheck.sha256(wrapper),wrapper.length()));
            File duplicate=zip(dir,"metadata.zip","[nemesis]\nid=catalog.hello\nid=catalog.other\nversion=1.0.0\n",null);
            rejected(()->PackageCheck.verify(duplicate,"catalog.hello","1.0.0",PackageCheck.sha256(duplicate),duplicate.length()));
            System.out.println("PASS: repository/HTTPS restrictions, redirects, hash/size checks, package identity, traversal, wrapper and duplicate metadata rejection");
        }finally{Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});}
    }
}
