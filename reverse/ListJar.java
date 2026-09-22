import java.util.zip.*;
import java.io.*;
import java.util.*;

/**
 * ListJar —— 从 nukkit.jar 里找「方块破坏 / 玩家动作 / 背包请求」相关类，
 * 并对指定类 dump 可读字符串（常量池里的方法名/字段名/字符串），用来逆向它的处理逻辑。
 */
public class ListJar {
    public static void main(String[] a) throws Exception {
        String jar = "/tmp/nukkit_server2/nukkit.jar";
        ZipFile z = new ZipFile(jar);
        String[] pats = { "PlayerAuthInput", "PlayerAction", "ItemStackRequest", "BlockAction", "InventoryTransaction", "UseItem" };
        List<String> hits = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
            String n = e.nextElement().getName();
            for (String p : pats) if (n.contains(p)) { hits.add(n); break; }
        }
        System.out.println("=== 命中类 (" + hits.size() + ") ===");
        for (String h : hits) System.out.println("  " + h);

        // 对两个关键类 dump 字符串
        for (String cls : new String[]{ "cn/nukkit/network/protocol/PlayerActionPacket.class",
                                        "cn/nukkit/network/protocol/PlayerAuthInputPacket.class" }) {
            ZipEntry en = z.getEntry(cls);
            System.out.println("\n=== " + cls + (en == null ? " (不在 jar 里)" : "") + " ===");
            if (en == null) continue;
            byte[] b = readAll(z.getInputStream(en));
            System.out.println("size=" + b.length);
            printStrings(b, 4, 40);
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) > 0) bo.write(tmp, 0, n);
        return bo.toByteArray();
    }

    static void printStrings(byte[] b, int minLen, int max) {
        int i = 0, cnt = 0;
        while (i < b.length && cnt < max) {
            if (b[i] >= 32 && b[i] < 127) {
                int j = i;
                while (j < b.length && b[j] >= 32 && b[j] < 127) j++;
                int len = j - i;
                if (len >= minLen) { System.out.println("   " + new String(b, i, len)); cnt++; }
                i = j;
            } else i++;
        }
    }
}