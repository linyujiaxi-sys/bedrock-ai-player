import cn.nukkit.plugin.PluginBase;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.level.Level;

/**
 * HeightProbe —— 给 Nukkit 补上「地面探测」能力
 * 命令: /ground <x> <z>  -> 返回该点最高的非空气方块 y
 * 用途: bot 客户端据此实现真重力 / 落地 / 自动跳台阶
 */
public class HeightProbe extends PluginBase {

    @Override
    public void onEnable() {
        getLogger().info("HeightProbe ready - 用 /ground <x> <z> 探测地面");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // ---- /block <x> <y> <z> : 看某个方块详情（id / meta / 类名 / 门是否打开）----
        if (command.getName().equalsIgnoreCase("block")) {
            if (args.length < 3) { sender.sendMessage("用法: /block <x> <y> <z>"); return true; }
            Level lv2 = null;
            for (Level l : getServer().getLevels().values()) { if ("world".equals(l.getName())) { lv2 = l; break; } }
            if (lv2 == null) lv2 = getServer().getDefaultLevel();
            try {
                int bx = (int) Math.floor(Double.parseDouble(args[0]));
                int by = (int) Math.floor(Double.parseDouble(args[1]));
                int bz = (int) Math.floor(Double.parseDouble(args[2]));
                cn.nukkit.block.Block b = lv2.getBlock(bx, by, bz);
                String open = "|open=?";
                try { open = "|open=" + b.getClass().getMethod("isOpen").invoke(b); } catch (Throwable t2) { }
                // 注意：Block.getDamage() 读到的是对象缓存的字段，不是世界真实数据，
                // 所以这里优先用 getBlockDataAt 读世界原始 meta
                int meta = b.getDamage();
                try { meta = lv2.getBlockDataAt(bx, by, bz); } catch (Throwable t3) { }
                sender.sendMessage("BLOCK|" + bx + "|" + by + "|" + bz + "|id=" + b.getId()
                        + "|meta=" + meta + "|" + b.getClass().getSimpleName() + open);
            } catch (Throwable t) { sender.sendMessage("BLOCK|ERR|" + t); }
            return true;
        }
    // ---- /spawnmob <实体名> [x y z] : 直接用 Entity.createEntity 造一只生物 ----
        if (command.getName().equalsIgnoreCase("spawnmob")) {
            if (args.length < 1) { sender.sendMessage("用法: /spawnmob <实体名> [x y z]"); return true; }
            Level lv4 = null;
            for (Level l : getServer().getLevels().values()) { if ("world".equals(l.getName())) { lv4 = l; break; } }
            if (lv4 == null) lv4 = getServer().getDefaultLevel();
            double x = -29, y = 70, z = 258;
            try {
                if (args.length >= 4) { x = Double.parseDouble(args[1]); y = Double.parseDouble(args[2]); z = Double.parseDouble(args[3]); }
            } catch (Throwable t) { }
            try {
                cn.nukkit.level.Position pos = new cn.nukkit.level.Position(x, y, z, lv4);
                cn.nukkit.entity.Entity en = cn.nukkit.entity.Entity.createEntity(args[0], pos);
                if (en == null) {
                    sender.sendMessage("SPAWN|创建失败（名字不对？）: " + args[0]);
                    return true;
                }
                sender.sendMessage("SPAWN|创建成功 " + en.getClass().getSimpleName() + " id=" + en.getId()
                        + " 位置=" + en.getFloorX() + "," + en.getFloorY() + "," + en.getFloorZ());
                en.spawnToAll();
                sender.sendMessage("SPAWN|已 spawnToAll");
            } catch (Throwable t) { sender.sendMessage("SPAWN|ERR|" + t); }
            return true;
        }
        // ---- /ents : 列出世界里所有实体（类型 + 数量）----
        if (command.getName().equalsIgnoreCase("ents")) {
            Level lv3 = null;
            for (Level l : getServer().getLevels().values()) { if ("world".equals(l.getName())) { lv3 = l; break; } }
            if (lv3 == null) lv3 = getServer().getDefaultLevel();
            java.util.Map<String, Integer> cnt = new java.util.TreeMap<>();
            int total = 0;
            for (cn.nukkit.entity.Entity en : lv3.getEntities()) {
                String k = en.getClass().getSimpleName();
                cnt.put(k, (cnt.containsKey(k) ? cnt.get(k) : 0) + 1);
                total++;
            }
            StringBuilder sb = new StringBuilder("ENTS|总数=" + total + "|");
            for (java.util.Map.Entry<String, Integer> e2 : cnt.entrySet()) {
                sb.append(e2.getKey()).append("×").append(e2.getValue()).append("  ");
            }
            sender.sendMessage(sb.toString());
            return true;
        }
        if (!command.getName().equalsIgnoreCase("ground")) return false;
        if (args.length < 2) { sender.sendMessage("用法: /ground <x> <z>"); return true; }
        double x, z;
        try { x = Double.parseDouble(args[0]); z = Double.parseDouble(args[1]); }
        catch (Exception e) { sender.sendMessage("坐标格式错误"); return true; }

        Level lvl = null;
        for (Level l : getServer().getLevels().values()) {
            if ("world".equals(l.getName())) { lvl = l; break; }
        }
        if (lvl == null) lvl = getServer().getDefaultLevel();

        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int y = 320; y >= -64; y--) {
            int id;
            try { id = lvl.getBlockIdAt(bx, y, bz); }
            catch (Throwable t) { id = lvl.getBlock(bx, y, bz).getId(); }
            if (id != 0) { // 0 = air
                sender.sendMessage("GROUND|" + x + "|" + z + "|" + y + "|" + id);
                return true;
            }
        }
        sender.sendMessage("GROUND|" + x + "|" + z + "|-999|0");
        return true;
    }
}