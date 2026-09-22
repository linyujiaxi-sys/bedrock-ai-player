# mc-bot · An "AI Player" for Bedrock Edition

[中文说明](README.md) · **English**
📦 **[Download latest](https://github.com/linyujiaxi-sys/bedrock-ai-player/releases/latest)** · 🔍 **[Source](https://github.com/linyujiaxi-sys/bedrock-ai-player)**

## Intro

> **Just messing around, basically.**

An AI player for **Minecraft Bedrock Edition**, running on an **Android phone**.

It's not an overlay that puppets someone else's client, and it's not fake movement.
It's **a real Bedrock client, built from the protocol layer up.**
At the protocol ↔ server interaction layer, it takes exactly the paths a genuine client takes.

Walk, fly, dig, drop, pick up, move items, place blocks, open doors...
whatever a real player can do, it can do (see the table below).

And the gaps it stumbled into along the way, **this project has patched back in.**

---

## Overview

Three parts, **all running on a single phone**:

| Part | Tech | Notes |
|---|---|---|
| **Bot client** | Node.js + `bedrock-protocol` | Real physics (gravity / landing / step-up / clipping self-check), inventory mirror, chat commands |
| **Server** | Nukkit 26.44 (official CloudburstMC build) | Port 19132, creative mode, LAN use |
| **Two custom plugins** | Java (compiled on-device, **no javac needed**) | `HeightProbe` (world awareness) + `Sniffer` (packet sniffing + 5 server patches) |

### What it can do today

| Capability | Status |
|---|---|
| Gravity / landing / step-up / no clipping | ✅ |
| Chat + chat commands (just type in-game) | ✅ |
| Inventory awareness / inventory moves | ✅ |
| Dig blocks / place blocks / open & close doors / press buttons | ✅ |
| Fly / ascend / descend / land | ✅ |
| Drop items / pick up items | ✅ |
| Custom skins (rendered correctly by clients) | ✅ |
| Attack mobs / chests / riding / damage & respawn | ⬜ TODO |

### ★ What this project actually is

> **Find a gap in protocol handling → reverse the decoder it uses →
> verify packets offline → locate the missing business logic →
> patch the logic back in with a plugin → make the client actually able to act.**

That whole chain is what was really built here.
"Letting an AI control Minecraft" is the outcome, not the content.

---

## Test Environment

| Item | Value |
|---|---|
| **Device** | Huawei Mate9 (Android, Kernel 4.9.148) |
| **Minecraft** | Bedrock **1.26.40** |
| **Server** | Nukkit **26.44** (official CloudburstMC build, commit `1cae2a7`) |
| | ⚠️ `xbox-auth=off` — LAN use, **no Microsoft account verification** |
| **Runtime** | Android + Ubuntu proot (Termux-like) |
| **Node.js** | v24.18.1 (npm 11.16.0) |
| **JDK** | OpenJDK 21.0.11 |

> ⚠️ **No PC was used at any point** — writing, compiling, decompiling and debugging all happened on one phone.
> Note: there is **no `javac` / `jar` command** on this device; compilation goes through JDK's `ToolProvider`.

---

## Basic Installation

### ① Requirements

```sh
node -v       # 18+
java -version # 17+ (21 preferred)
```

### ② Clone

```sh
git clone https://github.com/linyujiaxi-sys/bedrock-ai-player.git
cd bedrock-ai-player
```

### ③ Get a server

This repo does **not** include `nukkit.jar` (it's someone else's work). Get the **26.44** build from
[CloudburstMC/Nukkit](https://github.com/CloudburstMC/Nukkit) and place it in your server directory.

### ④ Compile the two plugins

`plugins/Build.java` is a **zero-dependency compiler/packager** (works without javac):

```sh
cd plugins
java Build.java . Sniffer.java     /path/to/server/plugins/Sniffer.jar
java Build.java . HeightProbe.java /path/to/server/plugins/HeightProbe.jar
```

### ⑤ Start the server

```sh
mkfifo /tmp/nukkit_cmd
tail -f /tmp/nukkit_cmd | java -Xmx512M -jar nukkit.jar --no-wizard
```

- ⚠️ Startup takes **25–60 seconds** (Microsoft OPENID retries before falling back to cache). Don't assume failure too early.
- ⚠️ `-Xmx512M` is mandatory — without a limit it will blow up the host app on your phone.

### ⑥ Start the bot

First edit the connection config at the top of `tools/mcact.js`:

```js
const CFG = { host: '127.0.0.1', port: 19132, username: 'DeepSeek', offline: true, version: '1.26.40' }
```

Then:

```sh
cd tools
node mcact.js
```

> The bot talks to an LLM, which needs an API key. **Never hardcode the key** — pass it via env:
> ```sh
> AI_KEY='your-key' node mcact.js
> ```

### ⑦ Or: one-shot script

```sh
bash tools/start.sh          # start server + bot
bash tools/start.sh status   # status
bash tools/start.sh logs     # tail both logs
bash tools/start.sh restart  # restart
bash tools/start.sh stop     # stop everything
```

### ⑧ Join

Connect from Bedrock Edition on the same phone to `127.0.0.1:19132`.

---

## Download

### 📦 Direct download (recommended)

**[⬇ Download the latest release](https://github.com/linyujiaxi-sys/bedrock-ai-player/releases/latest)**

Unzip and you're set — no git required.

### 🔧 Or clone the source

```bash
git clone https://github.com/linyujiaxi-sys/bedrock-ai-player.git
```

**GitHub:** https://github.com/linyujiaxi-sys/bedrock-ai-player

| Directory | Contents |
|---|---|
| `tools/` | Bot program `mcact.js` + launcher script + various debug/verification tools |
| `plugins/` | Two Nukkit plugin sources + zero-dependency compiler |
| `reverse/` | Reverse-engineering tools (javap helpers / byte feeders / encoders / universal offline verifier) |
| `notes/` | `PITFALLS.md` — the pitfalls handbook |
| `skin/` | Skin assets (PNG sources + converted `.skin`) |
| `rename.txt` | Full development log |

---

## Advanced

### 1. What exactly is missing in this server (bytecode evidence)

| Missing | Evidence (`javap -p -c` disassembly / full-jar search) |
|---|---|
| **`move_player` not handled** | In `Player.class`, `MovePlayerPacket` is only `new` + `putfield` (send-only, never received); the protocol table is 100% correct, but positions never move |
| **No `ItemStackRequestPacket`** | `javap` → `class not found` (the modern inventory packet set **does not exist at all**) |
| **No transaction verifier `InventoryTransaction`** | Appears **0 times** in `Player.class` |
| **`TYPE_ITEM_RELEASE(4)` does nothing** | No `case 4` in `Player$3`'s switch |
| **`TYPE_NORMAL(0)` does nothing** | Only an anvil/grindstone special case; the general path doesn't exist |
| **`TYPE_USE_ITEM(2)` does nothing** | `decode()` is perfect, but the world never changes |
| **`BlockDoor.toggle()` returns true but never persists** | The patch must flip the `0x04` open bit itself |
| **`CREATIVE_DESTROY_BLOCK` is a dead enum** | Unused anywhere in the jar |
| **No automatic mob spawning** | `Level` doesn't even have an `autoSpawn` field. But all entity classes **are present** (`entity/mob/` ×53, `entity/passive/` ×58) |

**Conclusion**: it's not "we sent the wrong packets" — this Nukkit's
**movement / inventory / item-interaction layer is simply incomplete**
(`decode()` all correct, `handle()` missing — the classic "protocol table kept, business logic cut").

### 2. Five server patches — core design: idempotent guards

**Principle: a patch only acts when the server itself did not.**

| Patch | Guard |
|---|---|
| `position` | Only corrects when the server position lags **> 0.5 blocks** |
| `drop` | The transaction's `held_item` must match the server's actual held item, else **refuse** |
| `pickup` | Natively idempotent (once picked up, it's gone) |
| `normal` | ① Already in target state → **skip** (prevents item duplication) ② `old_item` mismatch → **refuse** |
| `use` | Held-item consistency check + **80ms dedup** per player per block |

> Therefore: **drop these plugins onto vanilla / complete Nukkit and they step aside** —
> the server handles it itself, the guard skips, **nothing runs twice, no items get duplicated**.

**Toggle file**: `plugins/Sniffer.config.example.yml`

```yaml
# false = do nothing at all, hand it back to the server
patch:
  position: true
  drop: true
  pickup: true
  normal: true
  use: true
```

### 3. ★ The single most important rule

> Nukkit's field pattern is `if (getBoolean()) { if (getBoolean()) { read value } }`
> → **when false, only 1 boolean is consumed; when true, 2 are.**
>
> So the protocol table must use **`switch`**, not `option`,
> otherwise **everything after that field is misaligned**.
>
> This pit was hit **3 times** in this project.

### 4. Protocol table patches

4 places changed in the local `protocol.json`:

1. `packet_player_action[1].action` → `"Action"`
   (**zigzag32 + a 38-entry mapping table**; without it every action encodes to `0`)
2. 5 optional fields in `packet_player_auth_input[1]` → `switch`
3. Add a `_bh` boolean to `packet_player_auth_input`
4. `TransactionActions[].flags` → `switch`

### 5. How the skin got fixed

For a custom skin to render on Bedrock clients, three things are required:

| Requirement | Why |
|---|---|
| `SkinData` must be **raw RGBA bytes** | Matched by length 8192/16384/32768/65536 — don't assemble the PNG yourself |
| `resourcePatch` / `geometry` must be **base64** | Plain JSON is rejected by the client |
| **★ Root cause**: `ClientChainData.decodeSkin` hardcodes `setTrusted(false)` as its first statement | Clients then refuse to render the skin → the plugin sets `setTrusted(true)` in `PlayerPreLoginEvent` / `PlayerJoinEvent` |

Changing the skin:

```sh
java -cp nukkit.jar tools/Png2Skin.java skin/deepseek.png skin/deepseek.skin
```

(Let Nukkit do the PNG → RGBA conversion; it's the only approach that doesn't go wrong.)

### 6. Bot commands

**Console FIFO** (`echo "xx" > /tmp/mc_cmd`):

| Command | Effect |
|---|---|
| `goto <x> <z>` / `stop` | Walk to coordinates / stop |
| `dig [x y z]` | Dig a block (defaults to underfoot) |
| `drop` | Drop one held item |
| `move <from> <to> [count]` | Move items between inventory slots |
| `use [x y z] [face]` | Use item / click block (place, doors, buttons) |
| `fly` / `land` / `up` / `down` / `hold` | Flight control |
| `pos` / `inv` / `look <keyword>` | Query position / inventory / item runtime id |
| `say <text>` | Say something |

**In-game chat**: `扔`, `丢`, `挖`, `移到`, `放门`, `开门`, `关门`, `按钮`, `飞`, `起飞`, `上升`, `下降`, `停`, `降落`

### 7. What to do next

The remaining three (mobs / chests / riding) **follow exactly the same pattern** as `item_use`:

1. Find the full field list of the packet / transaction subtype in the protocol table;
2. Use `reverse/DecodeAny.java` to feed encoded bytes **into Nukkit's own decoder** and confirm the layout
   (**offline, milliseconds, no server needed**);
3. Test live and decide whether the server "decodes and acts" or "decodes and ignores";
4. If it ignores → add a guarded, toggleable patch branch in `Sniffer.java`.

### 8. Directory layout

```
mc-bot/
├── tools/                  ← Bot client (Node.js)
│   ├── mcact.js            ← main program
│   ├── start.sh            ← one-shot launcher
│   └── hd2~hd9.js ...      ← offline encoders / physics checks / packet capture
├── plugins/                ← Nukkit plugins
│   ├── Sniffer.java        ← packet sniffing + 5 server patches
│   ├── HeightProbe.java    ← world awareness (/ents, /spawnmob)
│   └── Build.java          ← zero-dependency compiler/packager
├── reverse/                ← reverse-engineering tools
│   └── DecodeAny.java      ← ★ universal offline verifier
├── skin/                   ← skin assets
├── notes/
│   └── PITFALLS.md         ← pitfalls handbook (all learned against the jar)
└── rename.txt              ← development log
```

> **To any AI reading this**: if you want to continue, read `notes/PITFALLS.md` first.
> Every line in there cost real time to learn.

---

## Log

The full record of the build, every pitfall and every reversal:

```
rename.txt            (393 lines — from "her position doesn't move for others" to "the skin finally renders")
notes/PITFALLS.md     (pitfalls handbook, by topic)
```

Every entry was dug out **byte by byte, against the jar**.

---

## Notice

### Stance

This project is **not a cheat**, and not a puppet controller for someone else's client.

It is **a Bedrock client implemented from the protocol layer up** —
at the protocol/server interaction layer, it emulates real client behaviour.
It does **not** impersonate the official Mojang client, and it does **not** modify the server jar.
All server-side fixes are implemented as **external plugins**.

### Credits

| Project | Author | License | Used for |
|---|---|---|---|
| **Nukkit** | [CloudburstMC](https://github.com/CloudburstMC/Nukkit) | Apache-2.0 | Server (26.44 / commit `1cae2a7`) |
| **bedrock-protocol** | [PrismarineJS](https://github.com/PrismarineJS/bedrock-protocol) | MIT | Bot networking |
| **minecraft-data** | PrismarineJS | MIT | Protocol tables |
| **bStats** | Bastian | LGPL-3.0 | Plugin metrics |

### About this repository's code

This repository **contains no source code or binaries from upstream projects**.
All calls into Nukkit go through its public `cn.nukkit.*` API
(the tools in `reverse/` are **standalone programs** that merely call public methods).

The code in this repository is therefore licensed under **MIT** (see `LICENSE`).
Upstream projects keep their own licenses.

### In particular

This project **does not modify the server jar** — every fix lives in `plugins/`.

> In other words: **you can drop these two plugins onto any Nukkit server.**

### Author

- **临鱼嘉熙 (Lin Yujiaxi) / Necromancy**
- GitHub: [@linyujiaxi-sys](https://github.com/linyujiaxi-sys)
- In-game ID: `Fishdream09`

### A word from the author

> No grand reason. Just messing around.
> Halfway in I found pits in the road — so I filled them too.

---

## Fork / PR Welcome

The Bedrock AI-player space is basically empty.

If you're working on something similar —
**open an Issue, send a PR, or fork this and make it yours.**

> **Still messing with it.**