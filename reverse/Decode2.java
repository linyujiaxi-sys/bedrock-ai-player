import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import java.lang.reflect.*;

/**
 * Decode2.java —— 直接把字节塞进 Nukkit 的 BinaryStream.buffer (byte[]) 然后 decode
 * 用法: java -cp nukkit.jar Decode2.java <hex>
 */
public class Decode2 {
    public static void main(String[] a) throws Exception {
        byte[] d = hex(a[0]);
        System.out.println("输入字节数: " + d.length);
        PlayerAuthInputPacket p = new PlayerAuthInputPacket();
        set(p, "buffer", d);
        try { set(p, "offset", 0); } catch (Throwable t) { }
        try { set(p, "count", d.length); } catch (Throwable t) { }
        try {
            p.decode();
        } catch (Throwable t) {
            System.out.println("### decode 抛异常: " + t);
            return;
        }
        System.out.println("### decode 成功，Nukkit 读到:");
        for (Field f : p.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            System.out.println("   " + f.getName() + " = " + f.get(p));
        }
    }

    static void set(Object o, String name, Object v) throws Exception {
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