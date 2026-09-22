import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import java.lang.reflect.*;
import java.nio.file.*;

/**
 * Search.java —— 字节变异搜索器
 * 思路: 拿我们生成的包(100字节)，在关键区间插入/删除不同数量的字节，
 *       每次喂给 Nukkit 自己的 decode，直到 blockActionData 非空。
 *       找到的那个布局 = Nukkit 真正期待的布局。
 * 用法: java -cp nukkit.jar Search.java
 */
public class Search {
    static byte[] base;

    public static void main(String[] a) throws Exception {
        base = hex(new String(Files.readAllBytes(Paths.get("/tmp/pkt.hex"))));
        System.out.println("基准包长度: " + base.length);

        int hit = 0;
        // 打印基准包的解码结果
        System.out.println("基准解码: " + brief(base));

        // 策略1: 在 offset 50..80 处插入 0..4 个零字节
        for (int ins = 1; ins <= 4 && hit < 6; ins++) {
            for (int pos = 50; pos <= 80 && hit < 6; pos++) {
                byte[] v = insert(base, pos, ins);
                String r = blockOf(v);
                if (r != null) {
                    System.out.println("★ 命中! 在 " + pos + " 处插入 " + ins + " 个字节 -> blockActionData=" + r);
                    Files.write(Paths.get("/tmp/pkt_good_" + pos + "_" + ins + ".hex"), toHex(v).getBytes());
                    hit++;
                }
            }
        }
        System.out.println("插入法命中数: " + hit);

        // 策略2: 删除 offset 50..80 处 1..3 个字节
        int hit2 = 0;
        for (int del = 1; del <= 3 && hit2 < 6; del++) {
            for (int pos = 50; pos <= 80 && hit2 < 6; pos++) {
                if (pos + del >= base.length) continue;
                byte[] v = remove(base, pos, del);
                String r = blockOf(v);
                if (r != null) {
                    System.out.println("★ 命中! 在 " + pos + " 处删除 " + del + " 个字节 -> blockActionData=" + r);
                    Files.write(Paths.get("/tmp/pkt_good_d" + pos + "_" + del + ".hex"), toHex(v).getBytes());
                    hit2++;
                }
            }
        }
        System.out.println("删除法命中数: " + hit2);
    }

    static String blockOf(byte[] d) {
        try {
            PlayerAuthInputPacket p = new PlayerAuthInputPacket();
            set(p, "buffer", d);
            set(p, "offset", 0);
            set(p, "count", d.length);
            p.decode();
            Object m = get(p, "blockActionData");
            if (m != null && m.toString().length() > 2) return m.toString();
        } catch (Throwable t) { }
        return null;
    }

    static String brief(byte[] d) {
        try {
            PlayerAuthInputPacket p = new PlayerAuthInputPacket();
            set(p, "buffer", d);
            set(p, "offset", 0);
            set(p, "count", d.length);
            p.decode();
            return "blockActionData=" + get(p, "blockActionData") + " tick=" + get(p, "tick");
        } catch (Throwable t) { return "异常 " + t; }
    }

    static void set(Object o, String name, Object v) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(o, v); return; }
            catch (NoSuchFieldException e) { }
        }
    }

    static Object get(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
            catch (NoSuchFieldException e) { }
        }
        return null;
    }

    static byte[] insert(byte[] b, int pos, int n) {
        byte[] r = new byte[b.length + n];
        System.arraycopy(b, 0, r, 0, pos);
        System.arraycopy(b, pos, r, pos + n, b.length - pos);
        return r;
    }

    static byte[] remove(byte[] b, int pos, int n) {
        byte[] r = new byte[b.length - n];
        System.arraycopy(b, 0, r, 0, pos);
        System.arraycopy(b, pos + n, r, pos, b.length - pos - n);
        return r;
    }

    static String toHex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    static byte[] hex(String s) {
        s = s.trim().replaceAll("[^0-9a-fA-F]", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
}