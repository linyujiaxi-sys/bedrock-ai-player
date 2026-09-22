import java.lang.reflect.*;
import java.nio.file.*;

/**
 * DecodeAny.java —— 万能离线验证器
 * 用法: java -cp nukkit.jar DecodeAny.java <Nukkit包类名> [hex文件]
 * 例:   java -cp nukkit.jar DecodeAny.java MovePlayerPacket /tmp/pkt.hex
 * 作用: 把字节喂给 Nukkit 自己的 decode，看它到底读到了什么
 */
public class DecodeAny {
    public static void main(String[] a) throws Exception {
        String cls = a[0];
        String file = a.length > 1 ? a[1] : "/tmp/pkt.hex";
        byte[] d = hex(new String(Files.readAllBytes(Paths.get(file))));
        Class<?> c = Class.forName("cn.nukkit.network.protocol." + cls);
        Object p = c.getDeclaredConstructor().newInstance();
        setF(p, "buffer", d);
        try { setF(p, "offset", 0); } catch (Throwable t) { }
        try { setF(p, "count", d.length); } catch (Throwable t) { }
        System.out.println(cls + " 输入字节数: " + d.length);
        try {
            c.getMethod("decode").invoke(p);
        } catch (Throwable t) {
            System.out.println("### decode 抛异常: " + (t.getCause() != null ? t.getCause() : t));
            return;
        }
        System.out.println("### Nukkit 读到:");
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            System.out.println("   " + f.getName() + " = " + f.get(p));
        }
    }

    static void setF(Object o, String name, Object v) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(o, v);
                return;
            } catch (NoSuchFieldException e) { }
        }
        throw new NoSuchFieldException(name);
    }

    static byte[] hex(String s) {
        s = s.trim().replaceAll("[^0-9a-fA-F]", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
}