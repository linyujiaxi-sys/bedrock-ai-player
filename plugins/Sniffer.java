import cn.nukkit.plugin.PluginBase;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.math.Vector3f;
import cn.nukkit.level.Location;
import java.lang.reflect.Field;

/**
 * Sniffer —— 窃听服务器收到的「动作包」
 * 用途: 搞清楚 bot 发的挖/丢包到底有没有到服务器、Nukkit 解析成了什么
 * 开关: 控制台 /sniff on | off
 */
public class Sniffer extends PluginBase implements Listener {
    private boolean on = true;
    private int mvc = 0, pc = 0;
    // item_use 去重表（同一玩家+同一方块 80ms 内只执行一次，防重复触发）
    private final java.util.Map<String, Long> lastUse = new java.util.HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        ensureConfig();
        // ★ 补丁3：自动拾取（魔改 Nukkit 的物品实体没被玩家捡起来）
        getServer().getScheduler().scheduleRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                if (!patchOn("pickup")) return;
                try {
                    for (cn.nukkit.Player p : getServer().getOnlinePlayers().values()) {
                        for (cn.nukkit.entity.Entity en : p.getLevel().getEntities()) {
                            double d = en.distance(p);
                            if (d < 3) {
                                if (en instanceof cn.nukkit.entity.item.EntityItem) {
                                    boolean ok = p.pickupEntity(en, true);
                                    getLogger().info("[物品] pickup=" + ok + " d=" + String.format("%.2f", d)
                                            + " spawned=" + refl(p, "spawned") + " alive=" + p.isAlive()
                                            + " online=" + p.isOnline() + " spec=" + p.isSpectator()
                                            + " closed=" + en.isClosed());
                                } else if (pc++ % 40 == 0) {
                                    getLogger().info("[近处] " + en.getClass().getSimpleName() + " d=" + String.format("%.2f", d));
                                }
                            }
                        }
                    }
                } catch (Throwable t) { }
            }
        }, 10);
        getLogger().info("Sniffer ready - 窃听 + 补丁(位置/丢东西/拾取)");
    }

    // ★★ 补丁：修复自定义皮肤不显示 ★★
    //   原因：Nukkit 的 ClientChainData.decodeSkin() 第一句就是 skin.setTrusted(false)（硬编码）。
    //   基岩版客户端对"不可信皮肤"（其他玩家视角）不渲染 → 显示默认皮肤。
    //   双时机保险：PreLogin（setSkin 之后）+ Join。
    @EventHandler
    public void onPreLogin(cn.nukkit.event.player.PlayerPreLoginEvent e) {
        fixSkin(e.getPlayer(), "PreLogin");
    }

    @EventHandler
    public void onJoin(cn.nukkit.event.player.PlayerJoinEvent e) {
        fixSkin(e.getPlayer(), "Join");
    }

    private void fixSkin(cn.nukkit.Player p, String from) {
        try {
            if (p == null) { getLogger().info("[皮肤] " + from + ": player=null"); return; }
            cn.nukkit.entity.data.Skin s = p.getSkin();
            getLogger().info("[皮肤] " + from + " 玩家=" + p.getName() + " skin=" + (s != null) + " trusted=" + (s != null ? s.isTrusted() : "?"));
            if (s != null && !s.isTrusted()) {
                s.setTrusted(true);
                getLogger().info("[皮肤] " + p.getName() + " -> trusted=true (来自 " + from + ")");
            }
        } catch (Throwable t) {
            getLogger().warning("[皮肤] " + from + " 失败: " + t);
        }
    }

    @EventHandler
    public void onPkt(DataPacketReceiveEvent e) {
        if (!on) return;
        String n = e.getPacket().getClass().getSimpleName();
        if (n.contains("PlayerAction") || n.contains("AuthInput") || n.contains("ItemStack") || n.contains("BlockPick") || n.contains("MovePlayer") || n.contains("InventoryTransaction")) {
            getLogger().info("PKT " + n + " | " + dump(e.getPacket()));
        if (n.contains("AuthInput")) {
                getLogger().info("  -> lastBlockAction=" + refl(e.getPlayer(), "lastBlockAction")
                        + " breakingBlock=" + refl(e.getPlayer(), "breakingBlock")
                        + " lastBreakPos=" + refl(e.getPlayer(), "lastBreakPosition"));
                // ★ 服务器端补丁：这个魔改 Nukkit 没有处理 move_player，
                //   我们从 player_auth_input 里取位置，替它把玩家位置同步上
                try {
                    if (e.getPacket() instanceof PlayerAuthInputPacket) {
                        Vector3f v = ((PlayerAuthInputPacket) e.getPacket()).getPosition();
                        // ★ 安全闸：只对机器人 DeepSeek 生效！绝不碰真人玩家
                        //   （否则真人客户端的 auth_input 也会被接管，导致被服务器拽着乱飞）
                        if (v != null && patchOn("position") && "DeepSeek".equals(e.getPlayer().getName())) {
                            // ★ 幂等守卫：只有服务器位置明显落后（>0.5格）才补。
                            // 若服务器自己已经同步好了 → 完全不动手（原版 Nukkit 上安全）
                            Location cur = e.getPlayer().getLocation();
                            double gap = Math.sqrt(Math.pow(cur.x - v.x, 2) + Math.pow(cur.y - v.y, 2) + Math.pow(cur.z - v.z, 2));
                            double gy = groundFor(e.getPlayer().getLevel(), v.x, v.y, v.z);
                            // ★ 服务器自己知道她在飞（isFlying）→ 飞着就不拉她下来
double ny = gy;
                            // ★ 极简规则（不再猜"她在不在飞"）：
                            //   她高于地面 → 完全信她（飞 / 悬停 / 跳都随她）
                            //   她低于地面 → 拉到地面（只防穿模和掉坑）
                            try {
                                ny = (v.y < gy) ? gy : v.y;
                                lastY.put(e.getPlayer().getName(), (double) v.y);
                            } catch (Throwable ty) { }
                            boolean yBad = Math.abs(cur.y - ny) > 0.6;
                            if (gap >= 8.0) {
                                // 有人 tp 了她（她走不了那么快）→ 接受服务器的新位置，不拉回
                                try {
                                    e.getPlayer().sendMessage("[位]" + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f,%.1f", cur.x, cur.y, cur.z, groundFor(e.getPlayer().getLevel(), cur.x, cur.y, cur.z)));
                                } catch (Throwable tz) { }
                            } else if (gap > 0.5 || yBad) {
                                // ★ 关键修复：必须用 teleport（Nukkit 会广播 MovePlayerPacket 给其他玩家）
                                //   setPosition 只改服务器内存里的数字，客户端永远看不到她动
                                Location loc = e.getPlayer().getLocation();
                                loc.x = v.x; loc.z = v.z;
                                loc.y = ny;
                                e.getPlayer().teleport(loc);
                                // ★ 把服务器权威坐标告诉她 —— 否则她本地永远偏 2 格，gap 永不收敛（无限重同步）
                                try {
                                    e.getPlayer().sendMessage("[位]"
                                            + String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f,%.1f", loc.x, loc.y, loc.z, gy));
                                } catch (Throwable tz) { }
                                pc++;
                                if (pc % 5 == 1) getLogger().info("[补丁] position 同步 -> "
                                        + Math.round(v.x * 100) / 100.0 + "," + Math.round(v.y * 100) / 100.0
                                        + "," + Math.round(v.z * 100) / 100.0 + " (gap=" + Math.round(gap * 100) / 100.0 + " 次=" + pc + ")");
                            }
                        }
                    }
                } catch (Throwable t) { }
            }
            if (n.contains("MovePlayer")) {
                mvc++;
                if (mvc % 20 == 1) getLogger().info("  ->服务器认为她在 " + e.getPlayer().getPosition());
            }
        }
    }

    @EventHandler
    public void onTx(cn.nukkit.event.server.DataPacketReceiveEvent e) {
        if (!(e.getPacket() instanceof cn.nukkit.network.protocol.InventoryTransactionPacket)) return;
        cn.nukkit.network.protocol.InventoryTransactionPacket pk =
                (cn.nukkit.network.protocol.InventoryTransactionPacket) e.getPacket();
        cn.nukkit.Player p = e.getPlayer();
        // 4 = TYPE_ITEM_RELEASE（丢东西）—— 魔改 Nukkit 没做，我们替它做
        if (pk.transactionType == 4) {
            if (!patchOn("drop")) return;
            try {
                cn.nukkit.inventory.PlayerInventory inv = p.getInventory();
                cn.nukkit.item.Item hand = inv.getItemInHand();
                // ★ 幂等守卫：事务里的 held_item 是"客户端认为的手持"。
                // 若服务器手持已经和它不一样 → 说明服务器自己处理过了 → 绝不再动手
                Object txd = refl(pk, "transactionData");
                Object txi = txd == null ? null : refl(txd, "itemInHand");
                if (txi instanceof cn.nukkit.item.Item) {
                    int want = ((cn.nukkit.item.Item) txi).getCount();
                    int cur = (hand == null || hand.isNull()) ? 0 : hand.getCount();
                    if (cur != want) {
                        getLogger().info("[补丁] 丢东西: 手持已变(服" + cur + "/客" + want + ") →服务器已处理，跳过");
                        return;
                    }
                }
                if (hand != null && !hand.isNull() && hand.getCount() > 0) {
                    cn.nukkit.item.Item one = hand.clone();
                    one.setCount(1);
                    int left = hand.getCount() - 1;
                    if (left > 0) { hand.setCount(left); inv.setItemInHand(hand); }
                    else { inv.setItemInHand(cn.nukkit.item.Item.get(0)); }
                    p.dropItem(one);
                    getLogger().info("[补丁] 丢出 " + one.getName() + " 剩余 " + left);
                }
            } catch (Throwable t) {
                getLogger().info("[补丁] 丢东西失败 " + t);
            }
        }
        // 0 = TYPE_NORMAL（背包内移动）—— 魔改 Nukkit 同样没做
        else if (pk.transactionType == 0) {
            if (!patchOn("normal")) return;
            try {
                cn.nukkit.inventory.PlayerInventory inv = p.getInventory();
                Object[] acts = (Object[]) refl(pk, "actions");
                int n = 0;
                // ★ 幂等守卫：先看服务器当前状态是不是【已经是事务的目标状态】。
                // 是 → 说明服务器自己处理过了（如原版 Nukkit）→ 补丁跳过，
                //      绝不重复执行 —— 这是防"物品复制"的关键闸门
                if (acts != null && acts.length > 0) {
                    boolean applied = true;
                    for (Object a2 : acts) {
                        Object st2 = refl(a2, "sourceType"), wi2 = refl(a2, "windowId");
                        Object sl2 = refl(a2, "inventorySlot"), ni2 = refl(a2, "newItem");
                        if (st2 == null || ((Number) st2).intValue() != 0) continue;
                        if (wi2 == null || ((Number) wi2).intValue() != 0 || sl2 == null) continue;
                        cn.nukkit.item.Item cur = inv.getItem(((Number) sl2).intValue());
                        cn.nukkit.item.Item want = (ni2 instanceof cn.nukkit.item.Item)
                                ? (cn.nukkit.item.Item) ni2 : cn.nukkit.item.Item.get(0);
                        if (!sameItem(cur, want)) { applied = false; break; }
                    }
                    if (applied) {
                        getLogger().info("[补丁] normal: 服务器已是目标状态 → 已处理过，跳过（防物品复制）");
                        return;
                    }
                }
                // ★ 一致性守卫：事务里的 old_item 必须等于服务器【当前】状态。
                // 不相等 → 说明双方认知不一致（客户端镜像过期 / 服务器已处理）
                // → 拒绝执行！绝不误覆盖别人的槽位。
                if (acts != null && acts.length > 0) {
                    boolean consistent = true;
                    for (Object a3 : acts) {
                        Object st3 = refl(a3, "sourceType"), wi3 = refl(a3, "windowId");
                        Object sl3 = refl(a3, "inventorySlot"), oi3 = refl(a3, "oldItem");
                        if (st3 == null || ((Number) st3).intValue() != 0) continue;
                        if (wi3 == null || ((Number) wi3).intValue() != 0 || sl3 == null) continue;
                        cn.nukkit.item.Item cur = inv.getItem(((Number) sl3).intValue());
                        cn.nukkit.item.Item had = (oi3 instanceof cn.nukkit.item.Item)
                                ? (cn.nukkit.item.Item) oi3 : cn.nukkit.item.Item.get(0);
                        if (!sameItem(cur, had)) {
                            getLogger().info("[补丁] normal: slot" + sl3 + " 实际内容与事务 old_item 不符 → 拒绝执行（防误覆盖）");
                            consistent = false;
                            break;
                        }
                    }
                    if (!consistent) return;
                }
                if (acts != null) {
                    for (Object a : acts) {
                        Object st = refl(a, "sourceType"), wi = refl(a, "windowId");
                        Object sl = refl(a, "inventorySlot"), ni = refl(a, "newItem");
                        if (st != null && ((Number) st).intValue() == 0
                                && wi != null && ((Number) wi).intValue() == 0 && sl != null) {
                            cn.nukkit.item.Item it = (ni instanceof cn.nukkit.item.Item)
                                    ? (cn.nukkit.item.Item) ni : cn.nukkit.item.Item.get(0);
                            inv.setItem(((Number) sl).intValue(), it);
                            getLogger().info("[补丁]   slot" + sl + " -> " + (it == null ? "air" : it.getName() + " x" + it.getCount()));
                            n++;
                        }
                    }
                }
                getLogger().info("[补丁] normal 事务执行 " + n + " 个槽位");
            } catch (Throwable t) {
                getLogger().info("[补丁] normal 失败 " + t);
            }
        }
        // 2 = TYPE_USE_ITEM（放方块 / 开关门 / 按钮）—— 魔改 Nukkit 同样是"解得出不干活"
        // 走 Nukkit 现成的 Level.useItemOn：它会自己分派 放置 / onActivate(门/按钮)
        else if (pk.transactionType == 2) {
            if (!patchOn("use")) return;
            try {
                cn.nukkit.Player pp = e.getPlayer();
                // ★ 安全闸：只对机器人生效！否则真人开门时会被补丁"再翻一次" → 门打开又自己关上
                if (!"DeepSeek".equals(pp.getName())) return;
                Object td = refl(pk, "transactionData");
                Object bp = refl(td, "blockPos");
                Object face = refl(td, "face");
                Object held = refl(td, "itemInHand");
                double bx = ((Number) refl(bp, "x")).doubleValue();
                double by = ((Number) refl(bp, "y")).doubleValue();
                double bz = ((Number) refl(bp, "z")).doubleValue();

                // 去重守卫：同一玩家 + 同一方块，80ms 内只执行一次
                String key = pp.getId() + ":" + (int) Math.floor(bx) + "," + (int) Math.floor(by) + "," + (int) Math.floor(bz);
                long now = System.currentTimeMillis();
                Long last = lastUse.get(key);
                if (last != null && now - last < 80L) {
                    getLogger().info("[补丁] item_use 去重跳过（80ms 内重复点击）");
                    return;
                }
                lastUse.put(key, now);

                cn.nukkit.item.Item it = (held instanceof cn.nukkit.item.Item)
                        ? (cn.nukkit.item.Item) held : cn.nukkit.item.Item.get(0);
                // 一致性守卫：事务里的手持物品必须和服务器实际手持一致（防不同步误操作）
                cn.nukkit.item.Item real = pp.getInventory().getItemInHand();
                int realId = (real == null || real.isNull()) ? 0 : real.getId();
                int txId = (it == null || it.isNull()) ? 0 : it.getId();
                if (realId != txId) {
                    getLogger().info("[补丁] item_use: 手持不符(服" + realId + "/客" + txId + ") → 拒绝执行");
                    return;
                }

                cn.nukkit.level.Level lvl = pp.getLevel();
                cn.nukkit.math.BlockFace bf = cn.nukkit.math.BlockFace.UP;
                try { if (face instanceof cn.nukkit.math.BlockFace) bf = (cn.nukkit.math.BlockFace) face; } catch (Throwable t2) { }

                cn.nukkit.block.Block target = lvl.getBlock((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz));

                // ★ 如果客户端报的位置不是门（门被捡走 / 坐标偏了）→ 在她身边 ±2 格扫描，找最近的门
                //   （她是"记得门的位置"，但门的位置可能已经变了）
                if (!(target instanceof cn.nukkit.block.BlockDoor)) {
                    int cxp = (int) Math.floor(pp.x), cyp = (int) Math.floor(pp.y), czp = (int) Math.floor(pp.z);
                    outer:
                    for (int ddx = -2; ddx <= 2; ddx++) {
                        for (int ddy = -2; ddy <= 1; ddy++) {
                            for (int ddz = -2; ddz <= 2; ddz++) {
                                cn.nukkit.block.Block bb = lvl.getBlock(cxp + ddx, cyp + ddy, czp + ddz);
                                if (bb instanceof cn.nukkit.block.BlockDoor) {
                                    target = bb;
                                    getLogger().info("[补丁] 开门: 目标非门 → 自动找到门 @" + (cxp + ddx) + "," + (cyp + ddy) + "," + (czp + ddz));
                                    break outer;
                                }
                            }
                        }
                    }
                }

                // ①-A 门：自己翻转开合位
                // （这个魔改版的 BlockDoor.toggle() 会"返回 true 但不写回世界"，所以不靠它）
                if (target instanceof cn.nukkit.block.BlockDoor) {
                    int meta0 = lvl.getBlock(target).getDamage();
                    cn.nukkit.block.Block low = ((meta0 & 0x08) != 0)   // 0x08 = TOP 位
                            ? lvl.getBlock(target.getFloorX(), target.getFloorY() - 1, target.getFloorZ())
                            : lvl.getBlock(target.getFloorX(), target.getFloorY(), target.getFloorZ());
                    cn.nukkit.block.Block up = lvl.getBlock(low.getFloorX(), low.getFloorY() + 1, low.getFloorZ());
                    low.setDamage((low.getDamage() ^ 0x04) & 0xFF);   // 0x04 = OPEN 位
                    up.setDamage((up.getDamage() ^ 0x04) & 0xFF);
                    lvl.setBlock(low, low, true);
                    bcBlock(lvl, lvl.getBlock(low));
                    lvl.setBlock(up, up, true);
                    bcBlock(lvl, lvl.getBlock(up));
                    getLogger().info("[补丁] 门已翻转开合：下 meta=" + lvl.getBlock(low).getDamage()
                            + " 上 meta=" + lvl.getBlock(up).getDamage());
                    return;
                }

                // ①-B 其他可交互方块（按钮 / 拉杆）→ 走 Nukkit 现成的 onActivate
                try {
                    if (target.canBeActivated() && target.onActivate(it, pp)) {
                        cn.nukkit.block.Block after = lvl.getBlock(target);
                        String o2 = "?";
                        try { o2 = "" + after.getClass().getMethod("isOpen").invoke(after); } catch (Throwable t9) { getLogger().info("[PATCH] dig-exception: " + t9); }
                        getLogger().info("[补丁] item_use → 交互成功 onActivate(" + target.getClass().getSimpleName()
                                + ") meta=" + after.getDamage() + " isOpen=" + o2);
                        return;
                    }
                } catch (Throwable t4) { getLogger().info("[补丁] onActivate 异常 " + t4); }

                // ② 否则是"放置方块"
                cn.nukkit.block.Block dst = target.getSide(bf);
                cn.nukkit.block.Block nb = it.getBlock();
                getLogger().info("[补丁] 诊断: canBePlaced=" + it.canBePlaced()
                        + " block=" + (nb == null ? "null" : nb.getClass().getSimpleName())
                        + " 放置位=" + dst.getFloorX() + "," + dst.getFloorY() + "," + dst.getFloorZ() + "(现 id=" + lvl.getBlock(dst).getId() + ")");
                boolean ok = false;
                if (nb != null) {
                    // ★ 不再调用 place()——Nukkit 的 place() 会改写周围方块（上次就是这样破坏了旁边的方块）
                    //   直接 setBlock 把它放下去
                    try { nb.x = dst.x; nb.y = dst.y; nb.z = dst.z; nb.level = lvl; } catch (Throwable t7) { }
                    if (true) {
                        try {
                            // ★ 门的下半必须清掉 0x08（TOP）位！否则上下两块都带 TOP 位
                            //   → 客户端把它当成"幽灵方块"渲染（透明、点不动）
                            if (nb instanceof cn.nukkit.block.BlockDoor) {
                                nb.setDamage(nb.getDamage() & ~0x08 & 0xFF);
                            }
                            lvl.setBlock(dst, nb);
                            bcBlock(lvl, lvl.getBlock(dst));
                            // 双格方块（门/床）必须补上半，否则 Nukkit 的 toggle/交互会失败
                            if (nb instanceof cn.nukkit.block.BlockDoor) {
                                cn.nukkit.block.Block up = (cn.nukkit.block.Block) nb.clone();
                                up.setDamage((nb.getDamage() | 0x08) & 0xFF);
                                try { up.x = dst.x; up.y = dst.y + 1; up.z = dst.z; up.level = lvl; } catch (Throwable t8) { }
                                lvl.setBlock(dst.up(), up);
                                bcBlock(lvl, lvl.getBlock(dst.up()));
                                getLogger().info("[补丁] 已补门上半 → y+1 id=" + lvl.getBlock(dst.up()).getId());
                            }
                            ok = true;
                            getLogger().info("[补丁] 兜底 setBlock → 放置位现在 id=" + lvl.getBlock(dst).getId());
                        }
                        catch (Throwable t6) { getLogger().info("[补丁] 兜底失败 " + t6); }
                    }
                }
                if (ok) {
                    int cnt = (it == null) ? 0 : it.getCount();
                    if (cnt <= 1) pp.getInventory().setItemInHand(cn.nukkit.item.Item.get(0));
                    else { cn.nukkit.item.Item n = it.clone(); n.setCount(cnt - 1); pp.getInventory().setItemInHand(n); }
                    getLogger().info("[补丁] item_use 已放置，手持剩 " + pp.getInventory().getItemInHand().getName());
                }
            } catch (Throwable t) {
                getLogger().info("[补丁] item_use 失败 " + t);
            }
        }
    }

    // ---- 补丁开关：plugins/Sniffer/config.yml ----
    // 每个补丁都带"幂等守卫"：只在【服务器自己没做】时才动手。
    // 换到原版/完整 Nukkit 上，服务器会自己处理，守卫就会自动跳过 → 不会重复执行。
    private void ensureConfig() {
        try {
            java.io.File d = getDataFolder();
            if (!d.exists()) d.mkdirs();
            java.io.File f = new java.io.File(d, "config.yml");
            if (!f.exists()) {
                java.nio.file.Files.write(f.toPath(),
                        ("# Sniffer 补丁开关（false = 完全不动手，交回服务器自己处理）\npatch:\n  position: true\n  drop: true\n  pickup: true\n  normal: true\n").getBytes("UTF-8"));
                reloadConfig();
                getLogger().info("[Sniffer] 已生成 config.yml（可开关各补丁）");
            }
        } catch (Throwable t) { getLogger().info("[Sniffer] config 初始化失败 " + t); }
    }

    private boolean patchOn(String k) {
        try { return getConfig().getBoolean("patch." + k, true); } catch (Throwable t) { return true; }
    }

    // 两个物品是不是同一个东西（空/空气都算 id=0 count=0）
    private boolean sameItem(cn.nukkit.item.Item a, cn.nukkit.item.Item b) {
        int ai = (a == null || a.isNull()) ? 0 : a.getId();
        int ac = (a == null || a.isNull()) ? 0 : a.getCount();
        int bi = (b == null || b.isNull()) ? 0 : b.getId();
        int bc = (b == null || b.isNull()) ? 0 : b.getCount();
        return ai == bi && ac == bc;
    }

    private final java.util.Map<String, Double> lastY = new java.util.HashMap<>();
    // ★ 飞行状态记忆：她一旦上升过，5 秒内都当作"在飞"，不再把她拉回地面
    private final java.util.Map<String, Long> flyUntil = new java.util.HashMap<>();
    // ★ 把方块变化广播给所有玩家
    //   （少了这一步，客户端看不到"放置动画"，还会自己回滚 → 门自己关上）
    private double groundFor(cn.nukkit.level.Level lv, double x, double y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int from = (int) Math.floor(y) + 2;
        for (int yy = from; yy >= from - 150; yy--) {
            cn.nukkit.block.Block b = lv.getBlock(bx, yy, bz);
            if (b.getId() != 0) return yy + 1;
        }
        return y;
    }
    private void bcBlock(cn.nukkit.level.Level lv, cn.nukkit.block.Block b) {
        try {
            cn.nukkit.network.protocol.UpdateBlockPacket pk =
                    new cn.nukkit.network.protocol.UpdateBlockPacket();
            pk.x = b.getFloorX();
            pk.y = b.getFloorY();
            pk.z = b.getFloorZ();
            pk.blockRuntimeId = cn.nukkit.level.GlobalBlockPalette.getOrCreateRuntimeId(b.getFullId());
            pk.flags = 15; // FLAG_ALL_PRIORITY
            pk.dataLayer = 0;
            for (cn.nukkit.Player p : lv.getPlayers().values()) {
                p.dataPacket(pk);
            }
        } catch (Throwable t) { }
    }

    private Object refl(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return "?" + t.getClass().getSimpleName();
        }
    }

    @EventHandler
    public void onBreak(cn.nukkit.event.block.BlockBreakEvent e) {
        if ("DeepSeek".equals(e.getPlayer().getName()) && e.getBlock().getId() == 0) {
            // 机器人挖到的是空气 → 自动往下找最近的实心方块
            //   （她的位置是补丁同步来的，服务器视角她可能悬空，脚下没方块）
            try {
                cn.nukkit.level.Level lv = e.getBlock().getLevel();
                int bx = e.getBlock().getFloorX(), by = e.getBlock().getFloorY(), bz = e.getBlock().getFloorZ();
                for (int dy = 1; dy <= 20; dy++) {
                    cn.nukkit.block.Block t = lv.getBlock(bx, by - dy, bz);
                    if (t.getId() != 0) {
                        lv.setBlock(t, cn.nukkit.block.Block.get(0), true);
                        bcBlock(lv, lv.getBlock(bx, by - dy, bz));
                        getLogger().info("[补丁] 挖掘: 原目标为空 → 改挖 id=" + t.getId() + " @y=" + (by - dy));
                        // ★ 把"真实地面高度"只告诉机器人（她悬空，需要知道地面在哪才能掉下去）
                        for (cn.nukkit.Player p2 : lv.getPlayers().values()) {
                            if ("DeepSeek".equals(p2.getName())) {
                                p2.sendMessage("[地]" + (by - dy));
                            }
                        }
                        break;
                    }
                }
            } catch (Throwable t9) { getLogger().info("[PATCH] dig-exception: " + t9); }
        }
        getLogger().info("[PATCH] check id=" + e.getBlock().getId() + " name=[" + e.getPlayer().getName() + "]");
        getLogger().info("BREAK id=" + e.getBlock().getId() + " @" + e.getBlock().getLocation() + " by " + e.getPlayer().getName() + " 玩家=" + e.getPlayer().getPosition());
    }

    @EventHandler
    public void onMove(cn.nukkit.event.player.PlayerMoveEvent e) {
        mvc++;
        if (mvc % 100 == 1) getLogger().info("MOVE " + e.getPlayer().getName() + " -> " + e.getTo());
    }

    private String dump(Object o) {
        StringBuilder sb = new StringBuilder();
        for (Field f : o.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                sb.append(f.getName()).append('=').append(f.get(o)).append(' ');
            } catch (Throwable t) { }
        }
        return sb.toString();
    }
}
