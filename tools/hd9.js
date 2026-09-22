/* hd9.js —— inventory_transaction / item_use（放方块 / 开关门 / 按钮）
 * 目标: 点 (-29,65,258) 的顶面 → 在 (-29,66,258) 放东西
 * 用法: node /tmp/hd9.js
 */
const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const proto = m.createSerializer('1.26.40').proto

const extra = { has_nbt: 'false', nbt: null, can_place_on: [], can_destroy: [] }
const item = (net, cnt) => ({
  network_id: net, count: cnt, metadata: 0,
  has_stack_id: false, stack_id: 0, block_runtime_id: 0, extra
})

const pkt = {
  transaction: {
    legacy: { legacy_request_id: 0, legacy_transactions: null },
    transaction_type: 'item_use',
    actions: [],
    transaction_data: {
      action_type: 'click_block',
      trigger_type: 'player_input',
      block_position: { x: -29, y: 65, z: 258 },
      face: 1,                       // 1 = 上面
      hotbar_slot: 0,
      held_item: item(335, 1),       // 占位(钻石)，真实运行时用 idmap 查到的门
      player_pos: { x: -28.7, y: 66, z: 258.7 },
      click_pos: { x: 0.5, y: 1.0, z: 0.5 },
      block_runtime_id: 0,
      client_prediction: 'success',
      client_cooldown_state: 'off'
    }
  }
}

try {
  const b = proto.createPacketBuffer('packet_inventory_transaction', pkt)
  console.log('LEN=' + b.length + ' HEX=' + b.toString('hex'))
  require('fs').writeFileSync('/tmp/pkt9.hex', b.toString('hex'))
} catch (e) {
  console.log('ERR ' + (e.stack || e.message))
}