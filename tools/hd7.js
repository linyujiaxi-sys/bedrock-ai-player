/* hd7.js —— 生成 inventory_transaction (丢东西 item_release) 的字节
 * 这是"手"的总开关：item_release=丢 / item_use=放置 / item_use_on_entity=攻击
 * 用法: node /tmp/hd7.js
 */
const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const proto = m.createSerializer('1.26.40').proto

const pkt = {
  transaction: {
    legacy: { legacy_request_id: 0, legacy_transactions: null },
    transaction_type: 'item_release',
    actions: [],
    transaction_data: {
      action_type: 'release',
      hotbar_slot: 0,
      held_item: {
        network_id: 264, count: 1, metadata: 0,
        has_stack_id: false, stack_id: 0,
        block_runtime_id: 0,
        extra: { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
      },
      head_pos: { x: 0, y: 0, z: 0 }
    }
  }
}

try {
  const b = proto.createPacketBuffer('packet_inventory_transaction', pkt)
  console.log('LEN=' + b.length + ' HEX=' + b.toString('hex'))
  require('fs').writeFileSync('/tmp/pkt.hex', b.toString('hex'))
} catch (e) {
  console.log('ERR ' + (e.stack || e.message))
}