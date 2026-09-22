import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Build.java —— 通用插件编译打包
 * 用法: java Build.java <目录> <主类.java> <输出jar>
 * 例:   java Build.java /tmp/sniffer Sniffer.java /tmp/nukkit_server2/plugins/Sniffer.jar
 */
public class Build {
    public static void main(String[] a) throws Exception {
        String dir = a[0], src = dir + "/" + a[1], jarOut = a[2];
        String out = dir + "/out";
        new File(out).mkdirs();
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        if (jc == null) { System.out.println("NO_COMPILER"); return; }
        int r = jc.run(null, null, null,
                "-classpath", "/tmp/nukkit_server2/nukkit.jar",
                "-d", out, src);
        System.out.println("compile_exit=" + r);
        if (r != 0) return;
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(jarOut))) {
            File[] fs = new File(out).listFiles();
            for (File f : fs) {
                if (!f.getName().endsWith(".class")) continue;
                z.putNextEntry(new ZipEntry(f.getName()));
                z.write(Files.readAllBytes(f.toPath()));
                z.closeEntry();
            }
            z.putNextEntry(new ZipEntry("plugin.yml"));
            z.write(Files.readAllBytes(Paths.get(dir + "/plugin.yml")));
            z.closeEntry();
        }
        System.out.println("JAR_OK " + jarOut);
    }
}