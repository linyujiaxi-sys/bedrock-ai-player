import java.util.zip.*;
import java.io.*;
import java.util.*;
public class FindCls {
  public static void main(String[] a) throws Exception {
    String key = a[0];
    ZipFile z = new ZipFile("/tmp/nukkit_server2/nukkit.jar");
    Enumeration<? extends ZipEntry> e = z.entries();
    while (e.hasMoreElements()) {
      ZipEntry en = e.nextElement();
      if (!en.getName().endsWith(".class")) continue;
      byte[] b = z.getInputStream(en).readAllBytes();
      if (new String(b, "ISO-8859-1").contains(key)) System.out.println(en.getName());
    }
  }
}
