import java.util.zip.*;
import java.io.*;

/** Dump —— 打印 jar 里指定类的可读字符串（扒字段名/方法名） */
public class Dump {
    public static void main(String[] a) throws Exception {
        ZipFile z = new ZipFile("/tmp/nukkit_server2/nukkit.jar");
        for (String cls : a) {
            ZipEntry en = z.getEntry(cls);
            System.out.println("\n=== " + cls + (en == null ? "  (不在 jar 里)" : "") + " ===");
            if (en == null) continue;
            byte[] b = z.getInputStream(en).readAllBytes();
            System.out.println("size=" + b.length);
            int i = 0, cnt = 0;
            while (i < b.length && cnt < 160) {
                if (b[i] >= 32 && b[i] < 127) {
                    int j = i;
                    while (j < b.length && b[j] >= 32 && b[j] < 127) j++;
                    int len = j - i;
                    if (len >= 4) { System.out.println("  " + new String(b, i, len)); cnt++; }
                    i = j;
                } else i++;
            }
        }
    }
}