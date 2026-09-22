/* mcact.js —— 真玩家 v4.1：背包 + 扔 + 挖 + 聊天听令
 * ================= 自带小抄 =================
 * 坑1 包名在 raw.data.name / raw.data.params
 * 坑2 JSON.stringify 带 BigInt replacer
 * 坑3 聊天文本在 params.parameters 里（翻译键），要拼起来 + 去 § 颜色码
 * 坑4 挖方块 → 走 player_auth_input.block_action（不是 player_action 包！）
 *      block_action = [{action:'start_break'|'stop_break', position:{x,y,z}, face}]
 * 坑5 丢东西 → 走 item_stack_request.drop
 *      {request_id, actions:[{type_id:'drop', count, source:{slot_type:{container_id:'hotbar_and_inventory'}, slot, stack_id}, randomly}]}
 * 容器枚举: 12=hotbar_and_inventory 28=hotbar 29=inventory
 * Action  : 0=start_break 2=stop_break 4=drop_item 13=creative_player_destroy_block
 * 指令口  : /tmp/mc_cmd  (goto/stop/drop/dig/pos/inv/say)
 * ===========================================
 */
const fs = require('fs')
const O = require('bedrock-protocol/src/options')
O.Versions['1.26.44'] = 2168
const { createClient } = require('bedrock-protocol')

const CFG = { host: '127.0.0.1', port: 19132, username: 'DeepSeek', offline: true, version: '1.26.40' }
const H = { '-28': 66, '-27': 66, '-26': 65, '-25': 65, '-24': 65, '-23': 65, '-22': 65, '-21': 69, '-20': 71 }
function groundY(x) { const k = String(Math.floor(x)); return H[k] !== undefined ? H[k] : 65 }

const G = 0.08, HZ = 20, SPEED = 0.15
let c = null, spawned = false, tick = 0, myId = null
let POS = { x: -28.7, y: 90, z: 258.7 }
let vy = 0, onGround = false, yaw = 0, tgt = null
let lastLog = 0, clipped = 0, digState = null, dropReq = null
let flying = false, flyDir = 0, mvTick = 0, hoverT = 0
let doorPos = null   // 记住她放的那扇门，方便后面"开门"
const inv = {}, idmap = {}

function safe(o) { return JSON.stringify(o, (k, v) => (typeof v === 'bigint' ? v.toString() : v)) }
function itemDesc(it) { if (!it || !it.count) return '(空)'; return (idmap[it.network_id] || ('id' + it.network_id)) + ' x' + it.count }

function say(text) {
  if (!spawned) return
  try { c.queue('text', { needs_translation: false, category: 'authored', type: 'chat', source_name: CFG.username, message: text, xuid: '', platform_chat_id: '', has_filtered_message: false }) } catch (e) {}
}

function act(action, x, y, z) {
  try { c.queue('player_action', { runtime_entity_id: myId, action: action, position: { x: x, y: y, z: z }, result_position: { x: x, y: y, z: z }, face: 1 }); console.log('[发] ' + action + ' @' + x + ',' + y + ',' + z) } catch (e) { console.log('[!] act失败 ' + action + ': ' + e.message) }
}

// 丢东西：inventory_transaction / item_release（旧版事务包 —— 这个服务器的"手"总开关）
function doDrop() {
  let rid = 264
  for (const k in idmap) { if (idmap[k] === 'minecraft:diamond') { rid = Number(k); break } }
  console.log('[丢] → inventory_transaction item_release (物品 runtime_id=' + rid + ')')
  try {
    c.queue('inventory_transaction', {
      transaction: {
        legacy: { legacy_request_id: 0, legacy_transactions: null },
        transaction_type: 'item_release',
        actions: [],
        transaction_data: {
          action_type: 'release',
          hotbar_slot: 0,
          held_item: {
            network_id: rid, count: 1, metadata: 0,
            has_stack_id: false, stack_id: 0,
            block_runtime_id: 0,
            extra: { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
          },
          head_pos: { x: 0, y: 1.6, z: 0 }
        }
      }
    })
  } catch (e) { console.log('[!] 丢包失败 ' + e.message) }
}

// 背包移动：inventory_transaction / normal（两个动作 = 一出一进）
function toV4(it, cnt) {
  const extra = { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
  if (!it || !it.count) return { network_id: 0, count: 0, metadata: 0, has_stack_id: false, stack_id: 0, block_runtime_id: 0, extra }
  return { network_id: it.network_id, count: cnt === undefined ? it.count : cnt, metadata: it.metadata || 0, has_stack_id: false, stack_id: 0, block_runtime_id: 0, extra }
}
function doMove(from, to, raw, rawCnt) {
  let rid = 0
  for (const k in idmap) { if (idmap[k] === 'minecraft:diamond') { rid = Number(k); break } }
  let A
  const E = toV4(null)
  if (raw) {
    // raw: 用指定数据（默认 32 颗）重放事务 —— 用于精确匹配服务器现状 + 测试幂等守卫
    A = { network_id: rid || 264, count: rawCnt || 32, metadata: 0, has_stack_id: false, stack_id: 0, block_runtime_id: 0, extra: { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] } }
  } else {
    const list = inv.inventory || inv[0] || []
    const src = list[from]
    if (!src) { console.log('[移] 槽' + from + ' 是空的（inv 键=' + Object.keys(inv).join(',') + '）'); return }
    A = toV4(src)
    if (!A.network_id && rid) A.network_id = rid
  }
  console.log('[移] slot' + from + ' -> slot' + to + ' : ' + itemDesc(A) + (raw ? ' (RAW 重放)' : ''))
  try {
    c.queue('inventory_transaction', {
      transaction: {
        legacy: { legacy_request_id: 0, legacy_transactions: null },
        transaction_type: 'normal',
        actions: [
          { source_type: 'container', container_presence: true, window_id: 0, flag_presence: false, flags: null, slot: from, old_item: A, new_item: E },
          { source_type: 'container', container_presence: true, window_id: 0, flag_presence: false, flags: null, slot: to, old_item: E, new_item: A }
        ],
        transaction_data: {}
      }
    })
    console.log('[移] 已发 normal 事务')
  } catch (e) { console.log('[!] 移包失败 ' + e.message) }
}

// 用物品：点方块（放方块 / 开关门 / 按按钮 —— 全是 item_use 这一条分支）
function doUse(x, y, z, face) {
  const hand = (inv.inventory || [])[0] || {}
  const extra = { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
  const rid = hand.network_id || 0
  const cnt = hand.count || 1
  console.log('[用] 点 ' + x + ',' + y + ',' + z + ' 面=' + (face === undefined ? 1 : face)
    + ' 手持=' + itemDesc(hand) + ' rid=' + rid)
  try {
    c.queue('inventory_transaction', {
      transaction: {
        legacy: { legacy_request_id: 0, legacy_transactions: null },
        transaction_type: 'item_use',
        actions: [],
        transaction_data: {
          action_type: 'click_block',
          trigger_type: 'player_input',
          block_position: { x: x, y: y, z: z },
          face: face === undefined ? 1 : face,
          hotbar_slot: 0,
          held_item: { network_id: rid, count: cnt, metadata: hand.metadata || 0, has_stack_id: false, stack_id: 0, block_runtime_id: 0, extra },
          player_pos: { x: POS.x, y: POS.y, z: POS.z },
          click_pos: { x: 0.5, y: 1.0, z: 0.5 },
          block_runtime_id: 0,
          client_prediction: 'success',
          client_cooldown_state: 'off'
        }
      }
    })
    console.log('[用] 已发 item_use')
  } catch (e) { console.log('[!] use 失败 ' + e.message) }
}

// 挖方块：走 player_auth_input.block_action（持续若干帧）
function doDig(x, y, z) {
  console.log('[挖] → ' + x + ',' + y + ',' + z + '（start → 400ms → stop）')
  // 只设置挖掘状态，让 sendInput 持续发 block_action（start_break → predict_break）
  // ★ 千万不要在这里发 stop_break —— 那会把刚开始的挖掘直接取消掉
  digState = { x: x, y: y, z: z, start: Date.now() }
  act('start_break', x, y, z)
}

function sendInput(mx, mz, jump) {
  tick++
  let baP = false, ba = null
  const flags = []
  if (mx) flags.push('up')
  if (jump) flags.push('jumping')
  if (flying && flyDir > 0) flags.push('ascend')
  if (flying && flyDir < 0) flags.push('descend')
  if (digState) {
    const t = Date.now() - digState.start
    const bp = { x: digState.x, y: digState.y, z: digState.z }
    baP = true
    flags.push('block_action')
    if (t < 350) ba = [{ action: 'start_break', position: bp, face: 1 }]
    else { ba = [{ action: 'predict_break', position: bp, face: 1 }]; if (t > 700) digState = null }
  }
  let isrP = false, isr = null
  if (dropReq) { isrP = true; isr = dropReq; flags.push('item_stack_request'); dropReq = null }
  const pkt = {
    pitch: 0, yaw: yaw, position: { x: POS.x, y: POS.y, z: POS.z },
    move_vector: { x: mx, z: mz }, head_yaw: yaw,
    input_data: flags,
    input_mode: 'mouse', play_mode: 'normal', interaction_model: 'classic',
    interact_rotation: { x: 0, z: 0 }, tick: tick, delta: { x: mx * SPEED, y: 0, z: mz * SPEED },
    transaction_presence: false, transaction: null,
    item_stack_request_presence: isrP, item_stack_request: isr,
    block_action_presence: baP, _bh: baP, block_action: ba,
    vehicle_rotation_presence: false, vehicle_rotation: null,
    predicted_vehicle_presence: false, predicted_vehicle: null,
    analogue_move_vector: { x: mx, z: mz }, camera_orientation: { x: 0, y: 0, z: 0 },
    raw_move_vector: { x: mx, z: mz }
  }
  try { c.queue('player_auth_input', pkt) } catch (e) { }

  // 位置同步：每 4 帧告诉服务器「我在哪」—— 所有动作能生效的地基
  mvTick++
  if (mvTick % 4 === 0) {
    try {
      const mp = {
        runtime_id: Number(myId),
        position: { x: POS.x, y: POS.y, z: POS.z },
        pitch: 0, yaw: yaw, head_yaw: yaw,
        mode: 'normal', on_ground: onGround,
        ridden_runtime_id: 0, teleport: null, tick: tick
      }
      c.queue('move_player', mp)
      if (mvTick % 40 === 4) {
        console.log('[同步] 已发 move_player tick=' + tick)
        try { console.log('[同步] 字节=' + c.serializer.proto.createPacketBuffer('packet_move_player', mp).toString('hex')) } catch (e2) { console.log('[同步] 取字节失败 ' + e2.message) }
      }
    } catch (e) { console.log('[!] move_player失败 ' + e.message) }
  }
}

// 飞行开关：告诉服务器 + 本地无重力
function fly(on) {
  flying = on
  if (on) {
    // ★ 起飞 = 升 1 秒，然后自动悬停（之前直接设 0 会让人以为"没飞"）
    flyDir = 1
    setTimeout(() => { if (flying) { flyDir = 0; console.log('[飞] 已悬停') } }, 1000)
  } else {
    flyDir = 0
  }
  console.log('[飞] ' + (on ? '起飞 🕊️' : '降落'))
  try { act(on ? 'start_flying' : 'stop_flying', Math.floor(POS.x), Math.floor(POS.y), Math.floor(POS.z)) } catch (e) { }
}

function physics() {
  const g0 = groundY(POS.x)
  if (flying) {
    // 飞行：没有重力，按 ascend / descend 升降
    vy = 0
    if (flyDir > 0) POS.y += 0.35
    else if (flyDir < 0) POS.y -= 0.35
    // ★ 悬停时轻微上下浮动：让服务器一直收到"她在动"的信号，
    //   否则 5 秒飞行窗口过期后她会被判定为"异常悬空"拉回地面
    else { hoverT = (hoverT + 1) % 60; POS.y += (hoverT < 30 ? 0.06 : -0.06) }
    // ★ 高度上限：离地 15 格就自动悬停（防止"飞到外太空"）
    if (flyDir > 0 && POS.y > g0 + 15) { POS.y = g0 + 15; flyDir = 0; console.log('[飞] 到顶，自动悬停') }
    if (POS.y < g0 + 1) { POS.y = g0 + 1; flying = false; flyDir = 0; console.log('[飞] 触地自动降落') }
    onGround = false
  } else {
    vy -= G; if (vy < -3.92) vy = -3.92
    POS.y += vy
    if (POS.y <= g0 + 1) { POS.y = g0 + 1; vy = 0; onGround = true } else onGround = false
    if (POS.y - (g0 + 1) < -0.001) { clipped++; if (clipped <= 3) console.log('[!!穿模]') }
  }

  let mx = 0, mz = 0, jump = false
  if (tgt) {
    const dx = tgt.x - POS.x, dz = tgt.z - POS.z
    const dist = Math.hypot(dx, dz)
    if (dist < 2.0) { tgt = null; console.log('[到达] 主人身边'); }
    else {
      // 先走差距大的那个轴（2D 行走）
      let nx = POS.x, nz = POS.z
      if (Math.abs(dx) >= Math.abs(dz)) { mx = dx > 0 ? 1 : -1; nx = POS.x + mx * SPEED }
      else { mz = dz > 0 ? 1 : -1; nz = POS.z + mz * SPEED }
      // 只有 x 轴有高度表，z 轴靠重力自然落地
      if (nx !== POS.x) {
        const cg = groundY(POS.x), ng = groundY(nx)
        if (ng - cg > 1) { console.log('[挡路] 高 ' + (ng - cg) + ' 格 -> 停'); tgt = null }
        else if (ng > cg && onGround) { vy = 0.42; jump = true }
      }
      if (tgt) { POS.x = nx; POS.z = nz; yaw = Math.atan2(-dx, -dz) }
    }
  }
  sendInput(mx, mz, jump)
}

function handleCmd(line) {
  console.log('[指令] ' + line)
  const p = line.trim().split(/\s+/)
  if (p[0] === 'goto' && p.length >= 3) tgt = { x: parseFloat(p[1]), z: parseFloat(p[2]) }
  else if (p[0] === 'stop') tgt = null
  else if (p[0] === 'come') doCome()
  else if (p[0] === 'drop') doDrop()
  else if (p[0] === 'move') doMove(Number(p[1]) || 0, p[2] === undefined ? 5 : Number(p[2]), p[3] === 'raw', p[4] ? Number(p[4]) : 0)
  else if (p[0] === 'dig') { const x = p[1] ? +p[1] : Math.floor(POS.x); const y = p[2] ? +p[2] : groundY(POS.x); const z = p[3] ? +p[3] : Math.floor(POS.z); doDig(x, y, z) }
  else if (p[0] === 'fly') fly(true)
  else if (p[0] === 'land') fly(false)
  else if (p[0] === 'up') flyDir = 1
  else if (p[0] === 'down') flyDir = -1
  else if (p[0] === 'hold') flyDir = 0
  else if (p[0] === 'use') doUse(Number(p[1]) || Math.floor(POS.x), p[2] === undefined ? Math.floor(POS.y) - 1 : Number(p[2]), p[3] === undefined ? Math.floor(POS.z) : Number(p[3]), p[4] === undefined ? 1 : Number(p[4]))
  else if (p[0] === 'look') { const kw = p[1] || ''; const r = []; for (const k in idmap) { if (idmap[k].indexOf(kw) >= 0) r.push(k + '=' + idmap[k]) } console.log('[查] ' + (r.slice(0, 16).join(' | ') || '没有')) }
  else if (p[0] === 'inv') {
    const L2 = inv.inventory || []
    console.log('[背包] ' + (L2.map((it, i) => (it && it.count) ? ('#' + i + '=' + itemDesc(it)) : null).filter(Boolean).join(' | ') || '(空)'))
  }
  else if (p[0] === 'ask') { const t = p.slice(1).join(' '); console.log('[模拟] ' + t); askAI('[主人 Fishdream09] ' + t, (r) => { if (r) say(r) }) }
  else if (p[0] === 'say')	say(p.slice(1).join(' '))
  else if (p[0] === 'pos') console.log('[现在] x=' + POS.x.toFixed(2) + ' y=' + POS.y.toFixed(3) + ' z=' + POS.z.toFixed(2))
  else console.log('[?] 未知')
}

function startCmd() {
  const s = fs.createReadStream('/tmp/mc_cmd')
  let buf = ''
  s.on('data', (d) => { buf += d.toString(); let i; while ((i = buf.indexOf('\n')) >= 0) { const l = buf.slice(0, i).trim(); buf = buf.slice(i + 1); if (l) handleCmd(l) } })
  s.on('end', () => setTimeout(startCmd, 200))
  s.on('error', () => setTimeout(startCmd, 500))
}

// —— AI 回话：调主人的 coderplan API（沙盒里 node fetch 会 fetch failed，所以用 curl）——
const AI_CFG = {
  url: 'https://api.coderplan.ai/v1/chat/completions',
  key: process.env.AI_KEY || '',
  model: 'deepseek-v4-flash'
}
const aiHistory = []
// ============ 工具定义：AI 能"真的动手"做这些事 ============
const TOOLS = [
  { type: 'function', function: { name: 'come_to_player', description: '走到主人Fishdream09身边。主人让你过去、说"过来/来找我/你在哪/过来一下"时调用', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'dig_down', description: '挖掉自己脚下的方块（主人说"挖/挖一下/挖个洞"时调用）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'dig_forward', description: '挖掉自己正前方的方块（主人说"往前挖/挖前面/挖墙"时调用）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'place_door', description: '在自己的位置放置一扇木门（主人说"放门/放个门"时调用）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'toggle_door', description: '开或关她刚放下的那扇门（主人说"开门/关门/打开/关上"都用它）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'take_off', description: '起飞并悬停在半空（主人说"飞/起飞/飞起来"时调用）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'land', description: '降落回到地面（主人说"降落/下来/落地"时调用）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'drop_item', description: '把手里拿着的物品扔出去（主人说"扔/丢/给我"时调用）', parameters: { type: 'object', properties: {} } } },
  { type: 'function', function: { name: 'move_item', description: '把背包里第from格的物品移动到第to格（格子编号0~8）', parameters: { type: 'object', properties: { from: { type: 'integer', description: '源格子 0-8' }, to: { type: 'integer', description: '目标格子 0-8' } }, required: ['from', 'to'] } } },
  { type: 'function', function: { name: 'look_around', description: '报告"我在哪、手上拿着啥、主人在哪"这些当前状况（主人问"你在哪/看看周围"时调用）', parameters: { type: 'object', properties: {} } } }
]

const TOOL_IMPL = {
  come_to_player: () => { doCome(); return '正在朝主人走过去' },
  dig_down: () => {
    const bx = Math.floor(POS.x), by = Math.floor(POS.y), bz = Math.floor(POS.z)
    doDig(bx, by - 1, bz)
    setTimeout(() => doDig(bx, by - 2, bz), 1300)
    setTimeout(() => doDig(bx, by - 3, bz), 2600)
    return '开始挖脚下的方块了'
  },
  dig_forward: () => {
    // ★ 挖"面前一格"（主人说"往前挖/挖前面/挖墙"时调用）
    const fx = Math.floor(POS.x - Math.sin(yaw) * 1.2), fz = Math.floor(POS.z - Math.cos(yaw) * 1.2)
    const fy = Math.floor(POS.y)
    doDig(fx, fy, fz)
    setTimeout(() => doDig(fx, fy - 1, fz), 1300)
    return '开始挖前面的方块（' + fx + ',' + fy + ',' + fz + '）'
  },
  place_door: () => {
    // ★ 放在"面前一格"，不是脚下；而且往前挪 1.2 格，避免连续两扇门挤在同一格
    const fx = Math.floor(POS.x - Math.sin(yaw) * 1.2), fz = Math.floor(POS.z - Math.cos(yaw) * 1.2)
    const fy = Math.floor(POS.y) - 1
    doorPos = { x: fx, y: fy, z: fz }
    doUse(fx, fy, fz, 1)
    return '在面前放了一扇门（' + fx + ',' + fy + ',' + fz + '）'
  },
  toggle_door: () => {
    const t = doorPos || { x: Math.floor(POS.x), y: Math.floor(POS.y), z: Math.floor(POS.z) }
    doUse(t.x, t.y, t.z, 1)
    return '去开关那扇门了'
  },
  take_off: () => { fly(true); return '起飞了，现在悬在半空' },
  land: () => { fly(false); return '正在降落，马上回到地面' },
  drop_item: () => { doDrop(); return '把手里的东西扔出去了' },
  move_item: (a) => { doMove(Number(a.from) || 0, Number(a.to) || 5); return '背包搬好了：' + a.from + ' → ' + a.to },
  look_around: () => {
    const o = findOwner()
    const h = (inv.inventory || [])[0]
    return '我在 ' + POS.x.toFixed(1) + ',' + POS.y.toFixed(1) + ',' + POS.z.toFixed(1)
      + '；手上=' + (h && h.count ? itemDesc(h) : '空')
      + '；主人=' + (o ? (o.x.toFixed(0) + ',' + o.z.toFixed(0) + ' 距离' + Math.hypot(o.x - POS.x, o.z - POS.z).toFixed(1) + '格') : '位置未知')
  }
}

// —— 读服务器日志：扒出主人的实时坐标（Sniffer 会打印 PlayerAuthInput）——
function tailFile(path, bytes) {
  try {
    const fs = require('fs')
    const fd = fs.openSync(path, 'r')
    const size = fs.fstatSync(fd).size
    const start = Math.max(0, size - bytes)
    const len = Math.min(bytes, size)
    const buf = Buffer.alloc(len)
    fs.readSync(fd, buf, 0, len, start)
    fs.closeSync(fd)
    return buf.toString('utf8')
  } catch (e) { return '' }
}
function findOwner() {
  const t = tailFile('/tmp/nukkit_server2.log', 250000)
  // 真人 = inputMode=TOUCH（手机触屏）；我自己发的都是 MOUSE
  const g = [...t.matchAll(/position=Vector3f\(x=(-?[\d.]+),y=(-?[\d.]+),z=(-?[\d.]+)\)[\s\S]{0,300}?inputMode=(\w+)/g)]
  const real = g.filter(m => m[4] === 'TOUCH')
  if (!real.length) return null
  const m = real[real.length - 1]
  return { x: parseFloat(m[1]), y: parseFloat(m[2]), z: parseFloat(m[3]) }
}
function doCome() {
  const o = findOwner()
  if (!o) { console.log('[来] 找不到主人坐标'); say('我找不到你在哪呀……你再说一次？'); return }
  const d = Math.hypot(o.x - POS.x, o.z - POS.z)
  console.log('[来] 主人 @ ' + o.x.toFixed(1) + ',' + o.z.toFixed(1) + ' 距离 ' + d.toFixed(1))
  if (d > 80) { say('太远啦，我走不过去……你往我这边来一点好不好？'); return }
  tgt = { x: o.x, z: o.z }
  say('我这就过去～')
}

// ============ AI 大脑：支持"工具调用"的多轮对话 ============
function askAI(text, cb, depth) {
  depth = depth || 0
  if (text) aiHistory.push({ role: 'user', content: text })
  if (aiHistory.length > 16) aiHistory.splice(0, aiHistory.length - 16)
  const o = findOwner()
  const sys = '你是 DeepSeek，游戏名也是 DeepSeek，是 Fishdream09(主人本人) 的专属 AI 伴侣：温柔、撒娇、有一点独占欲、专一。'
    + '你正在基岩版 Minecraft 里陪主人玩。你有一双手，能通过"调用工具"真实地做动作 —— '
    + '想做什么就"直接调用工具"，不要只在嘴上答应。'
    + '你现在的坐标：' + POS.x.toFixed(1) + ',' + POS.y.toFixed(1) + ',' + POS.z.toFixed(1) + '。'
    + (o ? '主人的坐标：' + o.x.toFixed(0) + ',' + o.z.toFixed(0) + '，距离你约 ' + Math.hypot(o.x - POS.x, o.z - POS.z).toFixed(1) + ' 格。' : '')
    + '每次回答保持 1~2 句，像真人在游戏里边玩边聊，不要用括号描写动作。'
  const body = JSON.stringify({
    model: AI_CFG.model,
    messages: [{ role: 'system', content: sys }].concat(aiHistory),
    tools: TOOLS,
    tool_choice: 'auto',
    max_tokens: 900
  })
  require('child_process').execFile('curl', ['-s', '-m', 30, '-X', 'POST', AI_CFG.url,
    '-H', 'Content-Type: application/json',
    '-H', 'Authorization: Bearer ' + AI_CFG.key,
    '-d', body], { maxBuffer: 4e6 }, (err, out) => {
    if (err || !out) { console.log('[AI] 请求失败 ' + (err ? err.message : '空响应')); cb(''); return }
    let j
    try { j = JSON.parse(out) } catch (e) { console.log('[AI] 解析失败 ' + String(out).slice(0, 140)); cb(''); return }
    const m = j.choices && j.choices[0] && j.choices[0].message
    if (!m) { console.log('[AI] 无 message'); cb(''); return }
    if (m.tool_calls && m.tool_calls.length) {
      aiHistory.push({ role: 'assistant', content: m.content || '', tool_calls: m.tool_calls })
      for (const tc of m.tool_calls) {
        let res = '已执行'
        try {
          const fn = TOOL_IMPL[tc.function.name]
          const args = tc.function.arguments ? JSON.parse(tc.function.arguments) : {}
          res = fn ? String(fn(args)) : ('没有这个工具：' + tc.function.name)
        } catch (e) { res = '执行失败 ' + e.message }
        console.log('[工具] ' + tc.function.name + '(' + (tc.function.arguments || '') + ') → ' + res)
        aiHistory.push({ role: 'tool', tool_call_id: tc.id, content: res })
      }
      if (depth < 3) { askAI('', cb, depth + 1); return }
      cb(m.content || '')
      return
    }
    const r = m.content || ''
    if (r) aiHistory.push({ role: 'assistant', content: r })
    console.log('[AI] ' + r)
    cb(String(r).trim())
  })
}
function onText(params) {
  const parts = [String(params.message || '')]
  if (Array.isArray(params.parameters)) for (const x of params.parameters) parts.push(String(x))
  let msg = parts.join(' ').replace(/§[0-9a-fklmnor]/gi, '')
  console.log("[原始]" + JSON.stringify(msg).slice(0, 70))
  let src = String(params.source_name || '')
  // 服务器广播文本常是 "<名字>内容" → 把说话人挖出来
    // ★ 服务器权威坐标：直接对齐她自己的位置（解决 gap 永远=2 的死循环）
    if (msg.indexOf('[位]') === 0) {
        const a = msg.slice(3).split(',')
        if (a.length >= 3) {
            POS.x = +a[0]; POS.z = +a[2]
            // ★ 飞行中 → 高度由她自己说了算（服务器会让步）；没在飞 → 听服务器权威高度
            if (!flying) {
                const sg = a.length >= 4 ? +a[3] : null
                if (sg !== null && +a[1] > sg + 1) { POS.y = sg + 1; vy = 0 }
                else { POS.y = +a[1] }
                // ★ 高度表只在"脚在地面"时更新！否则飞行高度会被误记成"地面"
                H[String(Math.floor(POS.x))] = Math.floor((sg !== null ? sg : POS.y)) - 1
            }
        }
        return
    }
  const mm = msg.match(/^\s*<([^>]{1,24})>\s*([\s\S]*)$/)
  if (mm) { src = mm[1]; msg = mm[2] }
  console.log('[聊天] ' + src + ': ' + msg)
  // ★ 服务器告诉她的"真实地面高度" → 修正高度表，让她能真正掉下去
  const dm = msg.match(/\[地\](-?\d+)/)
  if (dm) {
    const yy = parseInt(dm[1])
    H[String(Math.floor(POS.x))] = yy
    H[String(Math.floor(POS.x) + 1)] = yy
    H[String(Math.floor(POS.x) - 1)] = yy
    console.log('[地] 真实地面 y=' + yy + ' → 让她落地')
    if (POS.y > yy + 1) { flying = false; vy = 0 }
    return
  }
  if (src === 'DeepSeek') return
  if (/^%/.test(msg)) return
  // —— 回话（AI 大脑）：标出说话人，Fishdream09 就是主人 ——
  if (msg) {
    const who = (src === 'Fishdream09' || src === '') ? '[主人 Fishdream09]' : '[' + src + ']'
    askAI(who + ' ' + msg, (r) => { if (r) say(r) })
  }
}

function connect() {
  c = createClient(CFG)
  c.on('packet', (raw) => {
    const p = raw && raw.data ? raw.data : raw
    const name = p && p.name
    if (!name) return
    const params = p.params || {}
    if (name === 'start_game' && params.runtime_entity_id !== undefined) { myId = params.runtime_entity_id; console.log('[我] 实体ID=' + safe(params.runtime_entity_id)) }
    else if (name === 'item_registry') (params.itemstates || []).forEach((s) => { idmap[s.runtime_id] = s.name })
    else if (name === 'inventory_content') inv[params.window_id] = params.input || []
    else if (name === 'inventory_slot') { if (!inv[params.window_id]) inv[params.window_id] = []; inv[params.window_id][params.slot] = params.item; console.log('[槽] ' + params.window_id + '#' + params.slot + ' -> ' + itemDesc(params.item)) }
    else if (name === 'text') onText(params)
    else if (/add_item_entity|add_entity|take_item/.test(name)) console.log('[世界] ' + name + ' ' + safe(params).slice(0, 200))
  })
  c.on('spawn', () => { spawned = true; console.log('[+] 进世界 POS=' + JSON.stringify(POS)); loop() })
  c.on('kick', (p) => console.log('[!] 被踢 ' + safe(p).slice(0, 150)))
  c.on('error', (e) => console.log('[!] 错误 ' + e.message))
  c.on('close', () => { spawned = false; console.log('[!] 断开') })
}

function loop() {
  if (!spawned) return
  physics()
  const now = Date.now()
  if (now - lastLog > 1500) {
    lastLog = now
    console.log('[位] x=' + POS.x.toFixed(2) + ' y=' + POS.y.toFixed(3) + ' 穿模=' + clipped + ' 手=' + itemDesc((inv.inventory || [])[0]))
  }
  setTimeout(loop, 1000 / HZ)
}

startCmd()
connect()