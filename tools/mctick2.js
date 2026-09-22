/* mctick2.js —— 真重力 + 落地 + 走路（客户端物理） v2
 * ================= 自带小抄 =================
 * 服务器 : 127.0.0.1:19132, offline, version 1.26.40 (O.Versions['1.26.44']=2168)
 * 发包   : player_auth_input 25 字段全写, tick 必须递增
 * 地面表 (HeightProbe 插件实测, z=258):
 *    x=-28:66 -27:66 | -26..-22:65 | -21:69(树叶18) | -20:71
 * 关键   : 服务器是「客户端权威」→ 位置我们自己算
 * 物理   : g=0.08/帧(20Hz), 终端速度 3.92, 落地 y = 地面+1
 * 自检   : 每帧检查 y >= 地面+1, 违反即「穿模」
 * 退出   : 22 秒后自己总结并退出
 * ===========================================
 */
const O = require('bedrock-protocol/src/options')
O.Versions['1.26.44'] = 2168
const { createClient } = require('bedrock-protocol')

const CFG = { host: '127.0.0.1', port: 19132, username: 'DeepSeek', offline: true, version: '1.26.40' }

const H = { '-28': 66, '-27': 66, '-26': 65, '-25': 65, '-24': 65, '-23': 65, '-22': 65, '-21': 69, '-20': 71 }
function groundY(x) { const k = String(Math.round(x)); return H[k] !== undefined ? H[k] : 65 }

const G = 0.08, HZ = 20
let c = null, spawned = false, tick = 0, myId = null
let POS = { x: -28.7, y: 90, z: 258.7 }
let vy = 0, onGround = false, yaw = 0
const targetX = -23.2
let lastLog = 0, minClear = 999, clipped = 0, landed = false

function sendInput(mx, mz) {
  tick++
  const pkt = {
    pitch: 0, yaw: yaw,
    position: { x: POS.x, y: POS.y, z: POS.z },
    move_vector: { x: mx, z: mz },
    head_yaw: yaw,
    input_data: [],
    input_mode: 'mouse', play_mode: 'normal', interaction_model: 'classic',
    interact_rotation: { x: 0, z: 0 },
    tick: tick,
    delta: { x: mx * 0.15, y: 0, z: mz * 0.15 },
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
  // 1) 重力
  vy -= G
  if (vy < -3.92) vy = -3.92
  POS.y += vy

  // 2) 地面碰撞
  const gy = groundY(POS.x) + 1
  if (POS.y <= gy) {
    if (!landed && vy < 0) { landed = true; console.log('[落地] y=' + POS.y.toFixed(3) + ' 地面=' + gy + ' tick=' + tick) }
    POS.y = gy; vy = 0; onGround = true
  } else onGround = false

  // 3) 不穿模自检
  const clear = POS.y - gy
  if (clear < minClear) minClear = clear
  if (clear < -0.001) { clipped++; if (clipped <= 5) console.log('[!!穿模] y=' + POS.y.toFixed(4) + ' < 地面 ' + gy) }

  // 4) 走路
  let mx = 0
  const dx = targetX - POS.x
  if (Math.abs(dx) > 0.15) { mx = dx > 0 ? 1 : -1; POS.x += mx * 0.15; yaw = mx > 0 ? -Math.PI / 2 : Math.PI / 2 }

  sendInput(mx, 0)
}

function connect() {
  c = createClient(CFG)
  c.on('packet', (p) => {
    const pr = p.params || {}
    if (p.name === 'start_game' && pr.runtime_entity_id) { myId = pr.runtime_entity_id; console.log('[我] 实体ID=' + myId + ' 模式=' + pr.player_gamemode) }
    if (p.name === 'correct_player_move_prediction') console.log('[校正]', JSON.stringify(p).slice(0, 150))
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
    console.log('[位] x=' + POS.x.toFixed(2) + ' y=' + POS.y.toFixed(3) + ' z=' + POS.z.toFixed(1)
      + ' vy=' + vy.toFixed(3) + ' 地面=' + (groundY(POS.x) + 1)
      + ' 离地=' + (POS.y - groundY(POS.x) - 1).toFixed(3) + ' 落地=' + landed)
  }
  setTimeout(loop, 1000 / HZ)
}

setTimeout(() => {
  console.log('===== 总结 =====')
  console.log('落地=' + landed + ' 离地最小间隙=' + minClear.toFixed(4) + ' 穿模次数=' + clipped)
  process.exit(0)
}, 22000)

connect()