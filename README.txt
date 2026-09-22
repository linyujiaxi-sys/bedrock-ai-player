# mc-bot · 让基岩版也有「AI 玩家」

🌐 **[English](README_EN.md)** · 中文

## 简介

> **就是乱折腾的。**

一个跑在 **Android 手机**上的基岩版 AI 玩家。

起因很简单：服务器里太空了，想让它有点"人"气。

它不是"操控别人客户端的外挂"，也不是"假装模仿移动"——
而是 **从协议层，自己实现一个真实的 Bedrock 客户端**。
在「协议 ↔ 服务端」这一层，走的是**和真实客户端一样的路径**。

走路、飞、挖、丢、捡、搬、放方块、开关门……
真玩家能做的，它都能做（已实现的见下表）。

而在这条路上发现的服务端缺失，本项目**一并补了回来**。

---

## 概述

整套东西由三部分组成，**全部跑在一台手机上**：

| 部分 | 技术 | 说明 |
|---|---|---|
| **机器人客户端** | Node.js + `bedrock-protocol` | 真物理（自算重力/落地/跳台阶/穿模自检）、背包镜像、聊天听令 |
| **服务端** | Nukkit 26.44（CloudburstMC 官方构建） | 端口 19132，创造模式，局域网自用 |
| **两个自制插件** | Java（手机现场编译，**无需 javac**） | `HeightProbe`（世界感知）+ `Sniffer`（包窃听 + 5 个服务端补丁） |

### 已经能做什么

| 能力 | 状态 |
|---|---|
| 重力 / 落地 / 跳台阶 / 不穿模 | ✅ |
| 说话 + 聊天听令（游戏里打字就行） | ✅ |
| 背包感知 / 背包移动 | ✅ |
| 挖掘方块 / 放方块 / 开关门 / 按按钮 | ✅ |
| 飞行 / 上升 / 下降 / 降落 | ✅ |
| 丢东西 / 拾取 | ✅ |
| 自定义皮肤（能被客户端正确渲染） | ✅ |
| 攻击生物 / 箱子 / 骑乘 / 受伤重生 | ⬜ 待做 |

### ★ 这个项目真正的核心

> **发现协议处理缺口 → 逆向它自己的 decoder → 离线喂包验证 →
> 定位业务层缺失 → 用插件补回逻辑 → 让客户端真的能动起来。**

这一整条链，才是在这个项目里真正被做出来的东西。
「让 AI 控制 Minecraft」只是它的结果，不是它的内容。

---

## 测试环境

| 项目 | 内容 |
|---|---|
| **机型** | 华为 Mate9（Android，Kernel 4.9.148） |
| **我的世界** | 基岩版 **1.26.40** |
| **服务端** | Nukkit **26.44**（CloudburstMC 官方构建，commit `1cae2a7`） |
| | ⚠️ `xbox-auth=off` —— 局域网自用，**不做微软账号验证** |
| **运行环境** | Android + Ubuntu proot（Termux 系） |
| **Node.js** | v24.18.1（npm 11.16.0） |
| **JDK** | OpenJDK 21.0.11 |

> ⚠️ 这套东西**全程没有用电脑** —— 从写代码、编译、反编译到调试，都在一台手机上完成。
> 特别注意：手机上**没有 `javac` / `jar` 命令**，编译靠 JDK 自带的 `ToolProvider` 现场调用编译器。

---

## 基础安装教程

### ① 准备环境

```sh
node -v       # 需要 18 以上
java -version # 需要 17 以上（21 更好）
```

### ② 下载本仓库

```sh
git clone https://github.com/linyujiaxi-sys/bedrock-ai-player.git
cd bedrock-ai-player
```

### ③ 准备服务端

本仓库**不包含** `nukkit.jar`（它是别人的东西）。请自行获取
[CloudburstMC/Nukkit](https://github.com/CloudburstMC/Nukkit) 的 **26.44** 构建，
放在服务端目录下。

### ④ 编译两个插件

`plugins/Build.java` 是一个**零依赖的编译打包器**（手机上没 javac 也能用）：

```sh
cd plugins
java Build.java . Sniffer.java     /path/to/server/plugins/Sniffer.jar
java Build.java . HeightProbe.java /path/to/server/plugins/HeightProbe.jar
```

### ⑤ 启动服务端

```sh
mkfifo /tmp/nukkit_cmd
tail -f /tmp/nukkit_cmd | java -Xmx512M -jar nukkit.jar --no-wizard
```

- ⚠️ 启动需要 **25~60 秒**（微软 OPENID 超时重试后才走缓存），**别过早判失败**。
- ⚠️ `-Xmx512M` 是硬要求 —— 不限制内存会把手机上的宿主 App 撑爆。

### ⑥ 启动机器人

先改 `tools/mcact.js` 开头的连接配置：

```js
const CFG = { host: '127.0.0.1', port: 19132, username: 'DeepSeek', offline: true, version: '1.26.40' }
```

然后：

```sh
cd tools
node mcact.js
```

> 机器人接大模型要用到 API key，**不要把 key 写进代码**，用环境变量传：
> ```sh
> AI_KEY='你的key' node mcact.js
> ```

### ⑦ 或者：一键脚本

```sh
bash tools/start.sh          # 启动服务器 + 机器人
bash tools/start.sh status   # 看状态
bash tools/start.sh logs     # 看两边日志
bash tools/start.sh restart  # 重启
bash tools/start.sh stop     # 全部停止
```

### ⑧ 进游戏

手机上的基岩版直接连 `127.0.0.1:19132` 就行。

---

## 下载

**GitHub：** https://github.com/linyujiaxi-sys/bedrock-ai-player

| 目录 | 内容 |
|---|---|
| `tools/` | 机器人主程序 `mcact.js` + 一键启动脚本 + 各种调试/验证小工具 |
| `plugins/` | 两个 Nukkit 插件源码 + 零依赖编译打包器 |
| `reverse/` | 逆向工具（javap 辅助 / 字节喂食器 / 编码器 / 万能离线验证器） |
| `notes/` | `PITFALLS.md` —— 踩坑手册 |
| `skin/` | 皮肤素材（png 原图 + 转换好的 .skin） |
| `rename.txt` | 完整实战日志 |

---

## 进阶

### 一、这个服务端到底缺了什么（全部有字节码证据）

| 缺失 | 证据（`javap -p -c` 反汇编 / 全 jar 搜索） |
|---|---|
| **不处理 `move_player`** | `Player.class` 里 `MovePlayerPacket` 全是 `new` + `putfield`（只发不收）；协议表 100% 正确，但位置不动 |
| **没有 `ItemStackRequestPacket`** | `javap` → `class not found`（现代客户端的背包包**整套不存在**） |
| **没有事务校验器 `InventoryTransaction`** | 在 `Player.class` 里出现 **0 次** |
| **`TYPE_ITEM_RELEASE(4)` 不干活** | `Player$3` 的 switch 里没有 `case 4` |
| **`TYPE_NORMAL(0)` 不干活** | 只存在一个"锻造台/修理台特判"，通用处理路径不存在 |
| **`TYPE_USE_ITEM(2)` 不干活** | `decode()` 完美，但世界毫无变化 |
| **`BlockDoor.toggle()` 返回 true 却不落盘** | 补丁里必须自己翻转 `0x04` 开合位才生效 |
| **`CREATIVE_DESTROY_BLOCK` 是废枚举** | 全 jar 无人使用 |
| **没有"自动生成生物"逻辑** | `Level` 里连 `autoSpawn` 字段都没有。但实体类**全都在**（`entity/mob/` 53 个、`entity/passive/` 58 个） |

**结论**：不是"我们发包发错了"，而是这个 Nukkit 的
**移动 / 背包 / 物品交互层本身就不完整**
（`decode()` 全对、`handle()` 缺失 —— 典型的"协议表还在，业务逻辑被裁掉"）。

### 二、五个服务端补丁 —— 核心设计：幂等守卫

**设计原则：补丁只在「服务器自己没做」时才动手。**

| 补丁 | 守卫机制 |
|---|---|
| `position` | 只有服务器位置落后 **> 0.5 格**才补 |
| `drop` | 事务里的 `held_item` 必须等于服务器实际手持，不等 → 拒绝执行 |
| `pickup` | 原生幂等（捡走就不在了） |
| `normal` | ① 已是目标状态 → 跳过（防物品复制）② `old_item` 与服务器当前不符 → 拒绝执行 |
| `use` | 手持一致性检查 + 同一玩家同一方块 **80ms 去重** |

> 因此：**即使你把它丢到原版 / 完整的 Nukkit 上，它也会自动让位** ——
> 服务器自己会处理，守卫就跳过，**不会重复执行、不会复制物品**。

**开关文件**：`plugins/Sniffer.config.example.yml`

```yaml
# false = 完全不动手，交回服务器自己处理
patch:
  position: true
  drop: true
  pickup: true
  normal: true
  use: true
```

### 三、★ 最重要的一条通用规律

> Nukkit 的字段模式是 `if (getBoolean()) { if (getBoolean()) { 读值 } }`
> → **false 时只消耗 1 个布尔；true 时才消耗 2 个。**
>
> 所以协议表里必须写 **`switch`** 而不是 `option`，
> 否则**从那个字段起，后面整体错位**。
>
> 这个坑，全项目踩了 **3 次**。

### 四、协议表修补

本地 `protocol.json` 共改了 **4 处**：

1. `packet_player_action[1].action` → `"Action"`
   （**zigzag32 + 38 项映射表**；不映射的话所有动作都会被编成 `0`）
2. `packet_player_auth_input[1]` 的 5 个可选字段 → `switch`
3. `packet_player_auth_input` 里补一个 `_bh` 布尔
4. `TransactionActions[].flags` → `switch`

### 五、皮肤是怎么修好的

一个自定义皮肤要被基岩客户端正确渲染，三件事缺一不可：

| 要点 | 说明 |
|---|---|
| `SkinData` 必须是**原始 RGBA 字节** | 按字节长度 8192/16384/32768/65536 匹配，不要自己拼 png |
| `resourcePatch` / `geometry` 必须 **base64** | 直接塞明文 JSON 客户端不认 |
| **★ 根因**：`ClientChainData.decodeSkin` 第一句硬编码 `setTrusted(false)` | 客户端因此不渲染这张皮肤 → 插件在 `PlayerPreLoginEvent` / `PlayerJoinEvent` 里补 `setTrusted(true)` |

换皮肤的流程：

```sh
java -cp nukkit.jar tools/Png2Skin.java skin/deepseek.png skin/deepseek.skin
```

（让 Nukkit 自己完成 png → RGBA 的转换，这是唯一不会出错的做法。）

### 六、机器人指令

**控制台 FIFO**（`echo "xx" > /tmp/mc_cmd`）：

| 指令 | 作用 |
|---|---|
| `goto <x> <z>` / `stop` | 走向坐标 / 停下 |
| `dig [x y z]` | 挖方块（默认脚下） |
| `drop` | 丢出手持 1 个 |
| `move <from> <to> [数量]` | 背包格搬移 |
| `use [x y z] [face]` | 用物品 / 点方块（放方块、开关门、按钮） |
| `fly` / `land` / `up` / `down` / `hold` | 飞行控制 |
| `pos` / `inv` / `look <关键词>` | 查位置 / 查背包 / 查物品 runtime id |
| `say <文本>` | 说话 |

**游戏内聊天**：`扔`、`丢`、`挖`、`移到`、`放门`、`开门`、`关门`、`按钮`、`飞`、`起飞`、`上升`、`下降`、`停`、`降落`

### 七、下一步怎么接

剩下三件事（打怪 / 箱子 / 骑乘），**套路完全一样**，照着 `item_use` 走：

1. 先在协议表里找出这个包 / 事务子类型的完整字段；
2. 用 `reverse/DecodeAny.java` 把编码好的字节**喂给 Nukkit 自己的解码器**，
   确认布局正确（**离线毫秒级，不用起服务器**）；
3. 上线实测，判断服务器是"解得开且干活"还是"解得开不干活"；
4. 不干活 → 在 `Sniffer.java` 里加带**幂等守卫 + 开关**的分支补丁。

### 八、目录结构

```
mc-bot/
├── tools/                  ← 机器人客户端（Node.js）
│   ├── mcact.js            ← 主程序
│   ├── start.sh            ← 一键启动脚本
│   └── hd2~hd9.js ...      ← 离线编码器 / 物理验证 / 抓包工具
├── plugins/                ← Nukkit 插件
│   ├── Sniffer.java        ← 包窃听 + 5 个服务端补丁
│   ├── HeightProbe.java    ← 世界感知（/ents、/spawnmob）
│   └── Build.java          ← 零依赖编译打包器
├── reverse/                ← 逆向工具
│   └── DecodeAny.java      ← ★ 万能离线验证器
├── skin/                   ← 皮肤素材
├── notes/
│   └── PITFALLS.md         ← 踩坑手册（全部对着 jar 踩出来的）
└── rename.txt              ← 实战日志
```

> **给读这份文档的 AI**：如果你要继续做，请优先读 `notes/PITFALLS.md`。
> 里面每一条都是真金白银踩出来的，能省掉几小时。

---

## 日志

完整的实战记录、每一次踩坑和反转，都在：

```
rename.txt            （393 行 —— 从"她的位置在别人眼里不动"到"皮肤终于渲染出来"）
notes/PITFALLS.md     （踩坑手册，按主题分类）
```

每一条都是**对着 jar 一个字节一个字节啃出来的**。

---

## 声明

### 立场

本项目**不是外挂**，不是"操控别人的客户端"。

它是**从协议层自己实现一个 Bedrock 客户端** ——
在协议与服务端的交互层，模拟真实客户端的行为。
它**不冒充 Mojang 官方客户端**，也**不修改服务端 jar**，
所有对服务端的修复，都以**外部插件**形式实现。

### 致敬与署名

| 项目 | 作者 | 协议 | 用在哪 |
|---|---|---|---|
| **Nukkit** | [CloudburstMC](https://github.com/CloudburstMC/Nukkit) | Apache-2.0 | 服务端（26.44 / commit `1cae2a7`） |
| **bedrock-protocol** | [PrismarineJS](https://github.com/PrismarineJS/bedrock-protocol) | MIT | 机器人网络层 |
| **minecraft-data** | PrismarineJS | MIT | 协议表 |
| **bStats** | Bastian | LGPL-3.0 | 插件统计 |

### 关于本仓库的代码

本仓库**不包含**任何上游项目的源代码或二进制文件。
对 Nukkit 的调用，全部通过它公开的 `cn.nukkit.*` API 进行
（仓库中的 `reverse/` 工具也都是**独立的自写程序**，只是调用了它的公开方法）。

因此本仓库的代码采用 **MIT 许可证**（见 `LICENSE`）。
上游项目各自的许可证，仍归各自所有。

### 特别说明

本项目**没有修改服务端 jar 本身** —— 所有修复都在 `plugins/` 里。

> 也就是说：**你可以直接把这两个插件丢到任何一台 Nukkit 上。**

### 作者

- **临鱼嘉熙**（Necromancy）
- GitHub：[@linyujiaxi-sys](https://github.com/linyujiaxi-sys)
- 游戏 ID：`Fishdream09`

### 作者的话

> 没什么宏大理由，就是乱折腾。
> 折腾到一半发现前面有坑 —— 于是顺手把坑也填了。

---

## 欢迎 Fork / 欢迎 PR

基岩版的 AI 玩家这一块，基本上还是空白。

如果你也在做类似的事 ——
**欢迎开 Issue 聊，欢迎 PR，欢迎把这份文档 fork 走改成你自己的。**

> **就这样，还在折腾。**