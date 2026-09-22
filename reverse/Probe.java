import java.lang.reflect.*;
import cn.nukkit.network.protocol.PlayerActionPacket;

/**
 * Probe —— 用 Nukkit 自己的类，吐出「挖方块」动作包的真实字节
 * 运行: java -cp /tmp/nukkit_server2/nukkit.jar /tmp/Probe.java
 */
public class Probe {
    static void dumpFields(Class<?> c) {
        System.out.println("== " + c.getSimpleName() + " 字段 ==");
        for (Field f : c.getDeclaredFields()) {
            System.out.println("   " + f.getName() + " : " + f.getType().getName());
        }
    }

    public static void main(String[] a) throws Exception {
        dumpFields(PlayerActionPacket.class);

        // 打印 PlayerActionType 枚举
        Class<?> at = Class.forName("cn.nukkit.network.protocol.types.PlayerActionType");
        System.out.println("== PlayerActionType ==");
        for (Object e : at.getEnumConstants()) System.out.print(e + " ");
        System.out.println();

        PlayerActionPacket p = new PlayerActionPacket();
        set(p, "entityId", 1L);

        // action = START_BREAK (int)
        set(p, "action", 0);
        set(p, "x", -28); set(p, "y", 66); set(p, "z", 258);

        // resultPosition
        Class<?> bv = Class.forName("cn.nukkit.math.BlockVector3");
        Object pos = null;
        for (Constructor<?> ct : bv.getConstructors()) {
            Class<?>[] ps = ct.getParameterTypes();
            if (ps.length == 3) { pos = ct.newInstance(-28, 66, 258); break; }
        }
        set(p, "resultPosition", pos);
        set(p, "face", 1);

        p.encode();
        byte[] buf = (byte[]) invoke(p, "getBuffer");
        System.out.print("HEX(" + (buf == null ? -1 : buf.length) + "): ");
        if (buf != null) for (byte x : buf) System.out.printf("%02x ", x & 0xff);
        System.out.println();
    }

    static void set(Object o, String name, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        if (v instanceof Long) f.setLong(o, (Long) v);
        else if (v instanceof Integer) f.setInt(o, (Integer) v);
        else f.set(o, v);
    }

    static Object invoke(Object o, String m) throws Exception {
        try {
            Method mm = o.getClass().getMethod(m);
            return mm.invoke(o);
        } catch (NoSuchMethodException e) {
            for (Method mm : o.getClass().getMethods()) if (mm.getName().equals(m) && mm.getParameterCount() == 0) return mm.invoke(o);
            return null;
        }
    }
}