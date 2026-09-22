/* hd8.js —— inventory_transaction / normal（背包内移动物品）
 * 目标: 把 hotbar slot0 的钻石搬到 slot5
 * 用法: node /tmp/hd8.js
 */
const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const proto = m.createSerializer('1.26.40').proto

const extra = { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
const item = (net, cnt) => ({
  network_id: net, count: cnt, metadata: 0,
  has_stack_id: false, stack_id: 0, block_runtime_id: 0, extra
})
const AIR = item(0, 0)
const DIA = item(335, 32)

const action = (slot, oldI, newI) => ({
  source_type: 'container',
  container_presence: true, window_id: 0,
  flag_presence: false, flags: null,
  slot: slot, old_item: oldI, new_item: newI
})

const pkt = {
  transaction: {
    legacy: { legacy_request_id: 0, legacy_transactions: null },
    transaction_type: 'normal',
    actions: [
      action(0, DIA, AIR),   // slot0: 32颗钻石 -> 空
      action(5, AIR, DIA)    // slot5: 空 -> 32颗钻石
    ],
    transaction_data: {}
  }
}

try {
  const b = proto.createPacketBuffer('packet_inventory_transaction', pkt)
  console.log('LEN=' + b.length + ' HEX=' + b.toString('hex'))
  require('fs').writeFileSync('/tmp/pkt.hex', b.toString('hex'))
} catch (e) {
  console.log('ERR ' + (e.stack || e.message))
}