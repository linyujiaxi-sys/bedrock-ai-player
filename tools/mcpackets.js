/* mcpackets.js —— 背包抓包器 v1
 * 目的: 看服务器到底往客户端发了哪些「背包/物品」包
 * 用法: 起服务器后 nohup node /tmp/mcpackets.js > /tmp/mcpackets.log 2>&1 &
 *      然后服务器控制台 /give 给她东西，看客户端收不收得到
 * 14 秒后自己汇总退出
 */
const O = require('bedrock-protocol/src/options')
O.Versions['1.26.44'] = 2168
const { createClient } = require('bedrock-protocol')

const CFG = { host: '127.0.0.1', port: 19132, username: 'DeepSeek', offline: true, version: '1.26.40' }

let c = createClient(CFG)
const seen = {}

c.on('packet', (p) => {
  const n = seen[p.name] || 0
  seen[p.name] = n + 1
  if (/invent|item|slot|container|equip|hotbar|craft|held/i.test(p.name)) {
    console.log('[背包包] ' + p.name + ' #' + (n + 1) + ' ' + JSON.stringify(p.params || {}).slice(0, 350))
  }
})

c.on('spawn', () => {
  console.log('[+] 进世界，开始盯背包...')
  setTimeout(() => {
    console.log('===== 这 14 秒收到的所有包类型 =====')
    Object.keys(seen).sort().forEach((k) => console.log('  ' + k + '  x' + seen[k]))
    process.exit(0)
  }, 14000)
})

c.on('kick', (p) => console.log('[!] 被踢', JSON.stringify(p).slice(0, 150)))
c.on('error', (e) => console.log('[!] 错误', e.message))
c.on('close', () => console.log('[!] 断开'))