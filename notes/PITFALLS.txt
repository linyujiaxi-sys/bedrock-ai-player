# 踩坑手册（全部对着 `nukkit.jar` 踩出来的）

> 每一条都是花时间换来的。**做基岩版机器人/服务端开发，先读这篇能省几小时。**

---

## A. 协议层

### A1. ★★★ 可选字段的"布尔消耗"不统一（最坑的一个）

Nukkit 的字段读取模式是：

```java
if (stream.getBoolean()) {          // presence
    if (stream.getBoolean()) {      // option 的 bool
        值 = stream.getXXX();
    }
}
```

- **false → 只消耗 1 个布尔**
- **true  → 消耗 2 个布尔 + 值**

所以 `minecraft-data` 里的 `["option", X]` 写法**会多写一个布尔**，导致**从这个字段开始整体错位**。
**正确做法**：协议表里写成

```json
["switch", { "compareTo": "xxx_presence", "fields": { "true": X }, "default": "void" }]
```

本项目的 `packet_player_auth_input` 有 5 个可选字段全栽在这上面，
后来又栽在 `_bh`（`block_action` 额外布尔）和 `TransactionActions[].flags` 上 —— **一共 3 次**。

### A2. `player_action.action` 必须是 `zigzag32` + 映射表

如果协议表里只写裸的 `zigzag32`，**所有动作都会被编成 0**（服务器以为你永远在 `start_break`）。
必须映射：

```
0=start_break 2=stop_break 13=creative_player_destroy_block 4=start_flying ...
```

### A3. 破坏方块的正确姿势

| 动作 | 效果 |
|---|---|
| `start_break` / `stop_break` | **只影响提示/动画**，方块不会掉 |
| `creative_player_destroy_block(13)` | **废枚举，全 jar 无人使用** |
| **`predict_break(26)`** | ✅ **真正触发破坏的就是它** |

同时 `player_auth_input` 的 `input_data` 要带上 **`PERFORM_BLOCK_ACTIONS`(位 35)** 标志位，
否则服务器根本不看 `blockActions`。

### A4. `player_auth_input` 是"万能包"

现代客户端把**移动 / 挖方块 / 用物品 / 丢物品**全塞进这一个包里：

- 挖方块 → `blockActions` + 标志位 `PERFORM_BLOCK_ACTIONS(35)`
- 丢物品 → `itemStackRequest` + 标志位 `PERFORM_ITEM_STACK_REQUEST(36)`

**`InputData` 位表**（本项目实测）：

```
34 = item_interact     35 = block_action       36 = item_stack_request
6  = jumping           8  = sneaking           10 = up
21 = ascend_block      22 = descend_block
```

### A5. `inventory_transaction` 的结构

```
Transaction = {
  legacy,
  transaction_type : option { normal | inventory_mismatch | item_use | item_use_on_entity | item_release },
  actions          : option (array),
  transaction_data : switch (按 transaction_type)
}
```

- `transaction_type` / `actions` 的 **option 位是两个必须为 true 的布尔**
- `item_release` → `{action_type, hotbar_slot, held_item(ItemV4), head_pos}`
- `item_use` → `{action_type, trigger_type, block_position, face, hotbar_slot, held_item,
  player_pos, click_pos, block_runtime_id, client_prediction, client_cooldown_state}`
- `normal` → `actions` 里放两个 `NetworkInventoryAction`（**一出一进**）

### A6. `ItemV4` 的 `extra` 字段必须给完整对象

```js
extra: { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
```

`can_place_on` / `can_destroy` 必须是**空数组**（不是 `null`），否则 `SizeOf error`。

### A7. `NetworkInventoryAction` 的字段

```
source_type(0=container,1=global,2=world_interaction,3=creative)
container_presence(bool) → window_id(option<i8>)
flag_presence(bool)      → flags(option<varint>)   ← 注意 A1 的坑
slot(varint) / old_item(ItemV4) / new_item(ItemV4)
```

---

## B. Nukkit 使用层

### B1. `Block.getDamage()` 不是世界真值

它读的是 **Block 对象的缓存字段**，不是 chunk 里的真实 meta。
需要真值要用别的方式读（或换个位置的对象读）。

### B2. `Block.place()` 之前必须给方块对象设位置

```java
nb.x = dst.x; nb.y = dst.y; nb.z = dst.z; nb.level = lvl;
boolean ok = nb.place(item, 点击的方块, dst, face, 0.5, 1.0, 0.5, player);
```

不设 `x/y/z/level` → **`place()` 永远返回 false**（它内部 `getSide()` 找不到位置）。

### B3. 双格方块（门 / 床）要放两块

只放下半 → `toggle()` / 交互会失败。
上半的 meta 要带 `DOOR_TOP_BIT(0x08)`。

### B4. `BlockDoor.toggle()` 会"返回 true 但不落盘"

这个 fork 里它改的是对象字段，没写回世界。
**解法**：自己翻转开合位，上下两块一起写：

```java
low.setDamage((low.getDamage() ^ 0x04) & 0xFF);   // 0x04 = OPEN
up.setDamage((up.getDamage()  ^ 0x04) & 0xFF);
lvl.setBlock(low, low, true);
lvl.setBlock(up,  up,  true);
```

### B5. 创造模式下客户端的 `source_type` 不一样

客户端移动物品时，创造模式发的 `source_type` 和生存模式不同，
服务端里的 `actions.anyMatch(...)` 判定会因此走不同分支。

### B6. 门/双格方块的 meta 位（基岩）

```
0x04 = 开合位（OPEN）
0x08 = 上半位（TOP）
0x01 = 合页位（HINGE）
```

---

## C. 客户端（bedrock-protocol）

| # | 坑 | 真相 |
|---|---|---|
| C1 | 包名拿到 `undefined` | 在 **`raw.data.name`**，不是 `raw.name` |
| C2 | 聊天文本读不到 | 在 **`params.parameters`**：`[message, ...parameters].join(' ')`，且要 `replace(/§[0-9a-fklmnor]/gi,'')` 去色码 |
| C3 | `inv[0]` 是空的 | 背包容器的键是**字符串** `'inventory'`，不是数字 0 |
| C4 | `JSON.stringify` 炸 | BigInt 必须带 replacer：`(k,v)=>typeof v==='bigint'?v.toString():v` |
| C5 | 沙盒里 `fetch` 报 `fetch failed` | 用 `execFile('curl', ...)` 代替 |
| C6 | 序列化器"只吐 1 字节" | 要拿真身 `serializer.proto`，不是它本身 |
| C7 | 在线人数/名字对不上 | `/give` 要用**进服名**（如 `DeepSeek`），不是 gamertag |

---

## D. 环境 / 工具

| # | 坑 | 真相 |
|---|---|---|
| D1 | 手机没有 `screenrecord` | 只有 `screencap`；沙盒也没有 `/dev/shm` |
| D2 | 没有 `javac` / `jar` | 但有 `jdk.compiler` → 用 `ToolProvider.getSystemJavaCompiler()` 现场编译 |
| D3 | 反汇编怎么搞 | `java -m jdk.jdeps/com.sun.tools.javap.Main -p -c -classpath nukkit.jar <类>` |
| D4 | Nukkit 启动"卡住" | 要 **25~60 秒**（微软 OPENID 超时重试），别过早判失败 |
| D5 | 服务器把宿主 App 撑死 | 必须 `-Xmx512M` 限内存 |
| D6 | 文件管理器找不到 `/tmp` 里的东西 | 沙盒 `/tmp` ≠ `/sdcard`；要看就 `cp` 到 `/sdcard/Download/` |
| D7 | FIFO 发命令"没反应" | 时序问题：写入后要 `sleep` 4~6 秒再查询结果 |

---

## A8. 实体 / 生物（关于"服务器没有生物"）

| 判断 | 真相 |
|---|---|
| 怪物类和**包名** | Nukkit 的怪物**不在** `cn.nukkit.entity.monster`，而在 **`cn.nukkit.entity.mob`**（Zombie/Creeper/Skeleton…）；被动生物在 `cn.nukkit.entity.passive`。查错包名会误判成"类不存在" |
| 生物类齐不齐 | **齐全**：`mob/` 53 个 + `passive/` 58 个 + `projectile/` + `weather/` |
| 实体系统能不能用 | **能用**：`Entity.createEntity("Pig", new Position(x,y,z,level))` + `en.spawnToAll()` 实测成功 |
| 原生 `/summon` | **不可用**：任何语法都回 `%nukkit.command.summon.usage`（被权限/实现限制） |
| **"没生物"的真正原因** | **没有自动生成的 tick 逻辑**：`Level` 里没有 `autoSpawn` 字段，也没有 `getAutoSpawn()/setAutoSpawn()` |
| 怎么验证 | 自己写命令遍历 `level.getEntities()` 统计类型和数量（比盯日志可靠，日志有限流会骗人） |

**结论**：想"让世界活起来" → 自己写个插件，定时在玩家周围 `createEntity` + `spawnToAll` 即可，
**不需要**修引擎。（顺带还能控制刷什么、刷多少、离远了自动清掉。）

---

## F. 皮肤系统（自定义皮肤）

> 本节记录：2026-09-21。「数据全对、客户端就是不显示」→ 5 条弯路 → 根因。

### F1. ★★★ `skin.setTrusted(false)` 硬编码 —— 自定义皮肤永远不显示

**现象**：
用 `bedrock-protocol` 的 `options.skinData` 发送自定义皮肤，服务器日志显示「登入游戏」
（`Skin.isValid()` 校验通过），但**客户端里从头到尾显示默认皮肤**（注意：不是 Steve，是另一个默认外观）。

**排查走过的 5 条弯路**（全都不是根因）：

| # | 假设 | 验证结果 |
|---|---|---|
| 1 | 皮肤数据格式错（PNG 当成 RGBA） | 不是根因（但确实要修，见 F2） |
| 2 | 客户端按 `skinId` 缓存 | 换 skinId 无效 |
| 3 | 客户端按 UUID 缓存 | 换 UUID 无效 |
| 4 | 服务器发了 `player_skin` 包覆盖 | 服务器根本没发这个包 |
| 5 | geometry 用了空骨骼 `bones:[]` | 不是根因（但也是真 bug，见 F3） |

**真正的根因**（反编译 `cn.nukkit.utils.ClientChainData#decodeSkin`）：

```java
private Skin decodeSkin(JsonObject json) {
    Skin skin = new Skin();
    skin.setTrusted(false);        // ★ 方法第一句，硬编码 false，之后再也不改
    if (json.has("SkinId")) ...
}
```

**为什么会导致不显示**：
基岩版客户端 1.19.20+ 引入皮肤信任机制：
- `trusted = true`  → 正常渲染服务器发来的皮肤
- `trusted = false` → **看别人的时候**不渲染 → 回退默认皮肤

> 看自己不受影响 —— 客户端本来就知道自己的皮肤。
> 这也解释了为什么正版客户端自己看着一切正常，只有机器人"隐身"。

**修复**（插件补丁，不动 jar）：

```java
@EventHandler
public void onPreLogin(PlayerPreLoginEvent e) { fixSkin(e.getPlayer()); }

private void fixSkin(Player p) {
    Skin s = p.getSkin();
    if (s != null && !s.isTrusted()) s.setTrusted(true);   // ★
}
```

**时机关键**：`PlayerPreLoginEvent` 在 `Player.setSkin()` 之后、皮肤广播之前 → 在这里改才有效。

**验证**：服务器日志出现 `[皮肤] DeepSeek -> trusted=true` → 客户端立刻显示 ✅

### F2. `SkinData` 必须是「原始 RGBA」，不是 PNG 文件

**现象**：直接传 PNG → 服务器踢人：`disconnectionScreen.invalidSkin`

**Nukkit 的校验**（反编译 `SerializedImage#fromLegacy`）：

```java
switch (data.length) {
    case  8192: (64,32);    case 16384: (64,64);
    case 32768: (128,64);   case 65536: (128,128);
    default: throw new IllegalArgumentException("Unknown legacy skin size");
}
```

**它按字节长度精确匹配**：

| 传什么 | 结果 |
|---|---|
| PNG 文件字节（983） | ❌ 抛异常 → 被踢 |
| 原始 RGBA（64×64×4 = 16384） | ✅ 通过 |

另外 `SkinResourcePatch` 和 `SkinGeometryData` **都必须 base64 编码**
（`ClientChainData#decodeSkin` 里用 `Base64.getDecoder().decode()`）。

**正确做法：不要自己拼，用 Nukkit 自己的 API 转**：

```java
Skin s = new Skin();
s.setSkinData(bufferedImage);            // 交给它转
SerializedImage img = s.getSkinData();   // 取出来，格式 100% 一致
String b64 = Base64.getEncoder().encodeToString(img.data);
```

工具：`tools/Png2Skin.java`

**方法论**：
> **不确定格式的时候不要猜 —— 用对方自己的 API 转一遍。**

### F3. geometry 的 `bones` 不能是空数组

空骨骼能过服务器校验（只检查是不是合法 JSON），**但客户端拿不到模型 → 渲染不出来**。
需要完整 humanoid 骨骼：`body / head / hat / rightArm / leftArm / rightLeg / leftLeg`，
每个 cube 要给 `origin / size / uv`。

### F4. clientData 里的皮肤字段（对照表）

`bedrock-protocol` 登录时（`handshake/login.js:87`）：

```js
const customPayload = options.skinData || {}
payload = { ...payload, ...customPayload }   // ← 可覆盖任意 clientData 字段
```

需要的字段：

```
SkinId  SkinData  SkinImageWidth  SkinImageHeight
SkinGeometryData  SkinGeometryDataEngineVersion  SkinResourcePatch
PersonaSkin  PremiumSkin  CapeData  TrustedSkin  OverrideSkin
```

> ⚠️ `TrustedSkin: true` 写在 clientData 里**没用** ——
> Nukkit 的 `decodeSkin()` 会先 `setTrusted(false)` 把它覆盖掉，**必须用插件补丁（F1）**。

### F5. 换皮肤的标准流程

```
① 新 PNG 覆盖 skin/deepseek.png
② java -cp nukkit.jar tools/Png2Skin.java skin/deepseek.png skin/deepseek.skin
③ 重启机器人
```

`.skin` 文件格式：第一行 `宽 高`，第二行 `base64(RGBA)`。

---

## E. 方法论（比技术更重要）

1. **别猜协议，让服务器自己告诉你**：
   把字节塞进 `BinaryStream.buffer`，调 `packet.decode()`，看它读出什么
   → `reverse/DecodeAny.java`。**毫秒级、不用起服务器、不用机器人。**
2. **先判断"是解不出来"还是"解出来不干活"**：
   前者是协议表问题，后者是服务端缺逻辑，**两种修法完全不同**。
3. **补丁必须带幂等守卫**：
   只在服务器自己没做时才动手 → 换到原版服务器上也不会重复执行/复制物品。
4. **每个补丁都要有开关**（`config.yml`）→ 用户可以自己决定装不装。
5. **关键结论写进脚本头部注释**，不然三天后自己都忘了当初为什么这么写。
6. **数据全对、功能不生效时 → 往"信任 / 权限 / 校验位"想**：
   皮肤那个坑，数据、缓存、协议、geometry 全查过了都没问题，
   根因是服务端**硬编码的一个信任标志**（`skin.setTrusted(false)`）。
   这类"软开关"**日志里看不见、抓包也正常**，只有反编译才能发现。
   → 教训：**"能收到"≠"会被用"**，中间可能隔着一道看不见的闸门。