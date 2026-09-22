/* mctick3.js —— 可被 AI 实时指挥的「真玩家」 v3
 * ================= 自带小抄 =================
 * 指令口 : /tmp/mc_cmd  (FIFO)
 *    echo 'goto -20 258' > /tmp/mc_cmd   走向坐标
 *    echo 'jump' > /tmp/mc_cmd           原地跳
 *    echo 'stop' > /tmp/mc_cmd           停下
 *    echo 'say 你好' > /tmp/mc_cmd        说话
 *    echo 'pos' > /tmp/mc_cmd            报坐标
 * 物理   : g=0.08/帧(20Hz), 落地 y=地面+1
 * 碰撞   : 前方高 >1 格 → 挡住停下(防穿模); 高 1 格 → 自动跳
 * 地面表 (HeightProbe 实测 z=258):
 *    -28:66 -27:66 | -26..-22:65 | -21:69(树叶) | -20:71
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
let vy = 0, onGround = false, yaw = 0
let tgt = null
let lastLog = 0, minClear = 999, clipped = 0, jumps = 0

function say(text) {
  if (!spawned) return
  try { c.queue('text', { needs_translation: false, category: 'authored', type: 'chat', source_name: CFG.username, message: text, xuid: '', platform_chat_id: '', has_filtered_message: false }) } catch (e) {}
}

function sendInput(mx, mz, jump) {
  tick++
  const pkt = {
    pitch: 0, yaw: yaw,
    position: { x: POS.x, y: POS.y, z: POS.z },
    move_vector: { x: mx, z: mz },
    head_yaw: yaw,
    input_data: jump ? [6] : [],
    input_mode: 'mouse', play_mode: 'normal', interaction_model: 'classic',
    interact_rotation: { x: 0, z: 0 },
    tick: tick,
    delta: { x: mx * SPEED, y: 0, z: mz * SPEED },
    transaction_presence: false, transaction: null,
    item_stack_request_presence: false, item_stack_request: null,
    block_action_presence: false, block_action: null,
    vehicle_rotation_presence: false, vehicle_rotation: null,
    predicted_vehicle_presence: false, predicted_vehicle: null,
    analogue_move_vector: { x: mx, z: mz },
    camera_orientation: { x: 0, y: 0, z: 0 },
    raw_move_vector: { x: mx, z: mz }
  }
  try { c.queue('player_auth_input', pkt) } catch (e) {}
}

function physics() {
  // 重力
  vy -= G; if (vy < -3.92) vy = -3.92
  POS.y += vy
  // 落地
  const g0 = groundY(POS.x)
  if (POS.y <= g0 + 1) { POS.y = g0 + 1; vy = 0; onGround = true } else onGround = false
  // 不穿模自检
  const gy = groundY(POS.x) + 1
  const clear = POS.y - gy
  if (clear < minClear) minClear = clear
  if (clear < -0.001) { clipped++; if (clipped <= 5) console.log('[!!穿模] y=' + POS.y.toFixed(4) + ' < ' + gy) }

  // 行走
  let mx = 0, jump = false
  if (tgt) {
    const dx = tgt.x - POS.x, dz = tgt.z - POS.z
    if (Math.hypot(dx, dz) < 0.2) { tgt = null; console.log('[到达]') }
    else {
      const sx = Math.abs(dx) > 0.05 ? (dx > 0 ? 1 : -1) : 0
      const sz = Math.abs(dz) > 0.05 ? (dz > 0 ? 1 : -1) : 0
      const nx = POS.x + sx * SPEED
      const curG = groundY(POS.x), nxtG = groundY(nx)
      if (nxtG - curG > 1) { console.log('[挡路] 前方高 ' + (nxtG - curG) + ' 格 -> 停 @x=' + POS.x.toFixed(2)); tgt = null }
      else {
        if (nxtG > curG && onGround) { vy = 0.42; jump = true; jumps++; console.log('[跳] 上 1 格 @x=' + POS.x.toFixed(2)) }
        POS.x = nx
        if (sz) POS.z += sz * SPEED
        if (sx) yaw = sx > 0 ? -Math.PI / 2 : Math.PI / 2
        mx = sx
      }
    }
  }
  sendInput(mx, 0, jump)
}

function handleCmd(line) {
  console.log('[指令] ' + line)
  const p = line.trim().split(/\s+/)
  if (p[0] === 'goto' && p.length >= 3) { tgt = { x: parseFloat(p[1]), z: parseFloat(p[2]) }; console.log('[目标] ' + tgt.x + ',' + tgt.z) }
  else if (p[0] === 'stop') { tgt = null; console.log('[停]') }
  else if (p[0] === 'jump') { vy = 0.42 }
  else if (p[0] === 'say') say(p.slice(1).join(' '))
  else if (p[0] === 'pos') console.log('[现在] x=' + POS.x.toFixed(2) + ' y=' + POS.y.toFixed(3) + ' z=' + POS.z.toFixed(2) + ' 地面=' + (groundY(POS.x) + 1))
  else console.log('[?] 未知指令')
}

function startCmd() {
  const s = fs.createReadStream('/tmp/mc_cmd')
  let buf = ''
  s.on('data', (d) => {
    buf += d.toString()
    let i
    while ((i = buf.indexOf('\n')) >= 0) { const l = buf.slice(0, i).trim(); buf = buf.slice(i + 1); if (l) handleCmd(l) }
  })
  s.on('end', () => setTimeout(startCmd, 200))
  s.on('error', () => setTimeout(startCmd, 500))
}

function connect() {
  c = createClient(CFG)
  c.on('packet', (p) => {
    const pr = p.params || {}
    if (p.name === 'start_game' && pr.runtime_entity_id) { myId = pr.runtime_entity_id; console.log('[我] 实体ID=' + myId + ' 模式=' + pr.player_gamemode) }
  })
  c.on('spawn', () => { spawned = true; console.log('[+] 进世界 POS=' + JSON.stringify(POS)); loop() })
  c.on('kick', (p) => console.log('[!] 被踢:', JSON.stringify(p).slice(0, 150)))
  c.on('error', (e) => console.log('[!] 错误:', e.message))
  c.on('close', () => { spawned = false; console.log('[!] 断开') })
}

function loop() {
  if (!spawned) return
  physics()
  const now = Date.now()
  if (now - lastLog > 1000) {
    lastLog = now
    console.log('[位] x=' + POS.x.toFixed(2) + ' y=' + POS.y.toFixed(3) + ' 离地=' + (POS.y - groundY(POS.x) - 1).toFixed(3) + ' 跳=' + jumps + ' 穿模=' + clipped)
  }
  setTimeout(loop, 1000 / HZ)
}

startCmd()
connect()