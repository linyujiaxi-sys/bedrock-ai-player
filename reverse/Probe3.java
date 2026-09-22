import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.math.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Probe3.java —— 让 Nukkit 自己 encode 一个「带方块动作」的 player_auth_input
 * 这就是标准答案，拿它的十六进制和我们的对比，差在哪一目了然
 */
public class Probe3 {
    public static void main(String[] a) throws Exception {
        PlayerAuthInputPacket p = new PlayerAuthInputPacket();
        set(p, "pitch", 0f);
        set(p, "yaw", 0f);
        set(p, "headYaw", 0f);
        set(p, "position", new Vector3f(-28.7f, 66f, 258.7f));
        set(p, "motion", new Vector2(0, 0));

        Object ia = Cls("cn.nukkit.network.protocol.types.AuthInputAction", "from", 35);
        System.out.println("AuthInputAction.from(35) = " + ia);
        Set<Object> ids = new LinkedHashSet<>();
        ids.add(ia);
        set(p, "inputData", ids);

        set(p, "inputMode", Cls("cn.nukkit.network.protocol.types.InputMode", "fromOrdinal", 1));
        set(p, "playMode", Cls("cn.nukkit.network.protocol.types.ClientPlayMode", "fromOrdinal", 0));
        set(p, "interactionModel", Cls("cn.nukkit.network.protocol.types.AuthInteractionModel", "fromOrdinal", 2));
        set(p, "tick", 1L);
        set(p, "interactRotation", new Vector2f(0, 0));
        set(p, "delta", new Vector3f(0, 0, 0));

        Object pat = Cls("cn.nukkit.network.protocol.types.PlayerActionType", "from", 0);
        System.out.println("PlayerActionType.from(0) = " + pat);
        Class<?> patC = Class.forName("cn.nukkit.network.protocol.types.PlayerActionType");
        Class<?> pbadC = Class.forName("cn.nukkit.network.protocol.types.PlayerBlockActionData");
        Constructor<?> ctor = pbadC.getConstructor(patC, BlockVector3.class, int.class);
        Object pbad = ctor.newInstance(pat, new BlockVector3(-29, 65, 258), 1);
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(pat, pbad);
        set(p, "blockActionData", m);

        set(p, "vehicleRotation", new Vector2f(0, 0));
        set(p, "predictedVehicle", 0L);
        set(p, "analogMoveVector", new Vector2f(0, 0));
        set(p, "cameraOrientation", new Vector3f(0, 0, 0));
        set(p, "rawMoveVector", new Vector2f(0, 0));

        p.encode();
        byte[] buf = (byte[]) get(p, "buffer");
        int off = asInt(get(p, "offset"));
        int cnt = asInt(get(p, "count"));
        int n = cnt > 0 ? cnt : buf.length;
        System.out.println("offset=" + off + " count=" + cnt + " bufLen=" + buf.length + " 取前 " + n + " 字节");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < buf.length; i++) sb.append(String.format("%02x", buf[i]));
        System.out.println("标准答案 HEX=" + sb);

        PlayerAuthInputPacket q = new PlayerAuthInputPacket();
        set(q, "buffer", Arrays.copyOf(buf, Math.min(n, buf.length)));
        set(q, "offset", 0);
        set(q, "count", Math.min(n, buf.length));
        q.decode();
        System.out.println("回喂解码 blockActionData = " + get(q, "blockActionData"));
        System.out.println("回喂解码 tick = " + get(q, "tick") + " inputData=" + get(q, "inputData"));
    }

    static Object Cls(String name, String method, int arg) throws Exception {
        Class<?> c = Class.forName(name);
        for (Method m : c.getDeclaredMethods())
            if (m.getName().equals(method) && m.getParameterCount() == 1)
                return m.invoke(null, arg);
        throw new NoSuchMethodException(name + "." + method);
    }

    static void set(Object o, String name, Object v) {
        try {
            for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(o, v);
                    return;
                } catch (NoSuchFieldException e) { }
            }
            System.out.println("  (设置失败: " + name + ")");
        } catch (Throwable t) {
            System.out.println("  (设置失败: " + name + " " + t + ")");
        }
    }

    static Object get(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (Throwable e) { }
        }
        return null;
    }

    static int asInt(Object o) {
        return (o instanceof Number) ? ((Number) o).intValue() : -1;
    }
}