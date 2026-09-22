/* mcinv.js —— 接上她的背包 v1
 * 修掉两个坑：
 *   1) 包名在 raw.data.name / raw.data.params （不是 raw.name）
 *   2) JSON 里有 BigInt，要 safe 序列化
 * 功能：维护 inventory/armor/offhand 三个窗口的内容，收到就给物品"翻译"成名字
 * 16 秒后打印最终背包并退出
 */
const O = require('bedrock-protocol/src/options')
O.Versions['1.26.44'] = 2168
const { createClient } = require('bedrock-protocol')

const CFG = { host: '127.0.0.1', port: 19132, username: 'DeepSeek', offline: true, version: '1.26.40' }

let c = createClient(CFG)
const inv = {}
let idmap = {}

function safe(o) { return JSON.stringify(o, (k, v) => (typeof v === 'bigint' ? v.toString() : v)) }

function itemDesc(it) {
  if (!it || !it.count) return '(空)'
  const n = idmap[it.network_id] || ('id' + it.network_id)
  return n + ' x' + it.count
}

function showInv(w) {
  const a = inv[w] || []
  const used = a.filter((x) => x && x.count > 0)
  console.log('[背包] ' + w + ' 共' + a.length + '格 | 有东西: ' + (used.length ? used.map(itemDesc).join(' , ') : '(空)'))
}

c.on('packet', (raw) => {
  const p = raw && raw.data ? raw.data : raw
  const name = p && p.name
  if (!name) return
  const params = p.params || {}

  if (name === 'item_registry') {
    ;(params.itemstates || []).forEach((s) => { idmap[s.runtime_id] = s.name })
    console.log('[字典] 收到物品定义 ' + (params.itemstates || []).length + ' 种')
  } else if (name === 'inventory_content') {
    inv[params.window_id] = params.input || []
    console.log('[背包内容] ' + params.window_id + ' -> ' + (params.input || []).length + ' 格')
    showInv(params.window_id)
  } else if (name === 'inventory_slot') {
    if (!inv[params.window_id]) inv[params.window_id] = []
    inv[params.window_id][params.slot] = params.item
    console.log('[槽变化] ' + params.window_id + ' slot#' + params.slot + ' -> ' + itemDesc(params.item))
  } else if (name === 'player_hotbar' || name === 'mob_equipment') {
    console.log('[' + name + '] ' + safe(params).slice(0, 200))
  }
})

c.on('spawn', () => {
  console.log('[+] 进世界，开始记录背包...')
  setTimeout(() => {
    console.log('===== 最终背包 =====')
    Object.keys(inv).forEach(showInv)
    process.exit(0)
  }, 16000)
})

c.on('kick', (p) => console.log('[!] 被踢', safe(p).slice(0, 150)))
c.on('error', (e) => console.log('[!] 错误', e.message))
c.on('close', () => console.log('[!] 断开'))