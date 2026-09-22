/* hd5.js —— 生成 move_player（位置同步包）的字节，写到 /tmp/pkt.hex
 * 用法: node /tmp/hd5.js
 */
const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const proto = m.createSerializer('1.26.40').proto

const pkt = {
  runtime_id: 1,
  position: { x: -28.7, y: 66.0, z: 258.7 },
  pitch: 0, yaw: 0, head_yaw: 0,
  mode: 'normal',
  on_ground: true,
  ridden_runtime_id: 0,
  teleport: null,
  tick: 123
}

try {
  const b = proto.createPacketBuffer('packet_move_player', pkt)
  console.log('LEN=' + b.length + ' HEX=' + b.toString('hex'))
  require('fs').writeFileSync('/tmp/pkt.hex', b.toString('hex'))
} catch (e) {
  console.log('ERR ' + e.message)
}