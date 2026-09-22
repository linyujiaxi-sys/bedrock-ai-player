import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import io.netty.buffer.Unpooled;
import java.lang.reflect.*;

/**
 * Decode.java —— 把十六进制字节直接喂给 Nukkit 自己的解码器
 * 用法: java Decode.java <hex字符串>
 * 效果: 打印 Nukkit 真实解析出来的每个字段（这是我们唯一不用猜的真相）
 */
public class Decode {
    public static void main(String[] a) throws Exception {
        byte[] d = hex(a[0]);
        System.out.println("输入字节数: " + d.length);
        PlayerAuthInputPacket p = new PlayerAuthInputPacket();

        Method m = null;
        for (Method x : p.getClass().getMethods()) {
            if (x.getName().equals("setBuffer") && x.getParameterCount() == 1
                    && x.getParameterTypes()[0].getName().equals("io.netty.buffer.ByteBuf")) { m = x; break; }
        }
        if (m != null) {
            m.invoke(p, Unpooled.wrappedBuffer(d));
        } else {
            Field bf = null;
            for (Class<?> c = p.getClass(); c != null && bf == null; c = c.getSuperclass()) {
                try { bf = c.getDeclaredField("buffer"); } catch (Throwable t) { }
            }
            if (bf == null) {
                System.out.println("找不到 buffer 字段 / setBuffer");
                return;
            }
            bf.setAccessible(true);
            bf.set(p, Unpooled.wrappedBuffer(d));
        }
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

    static byte[] hex(String s) {
        s = s.trim().replaceAll("[^0-9a-fA-F]", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
}