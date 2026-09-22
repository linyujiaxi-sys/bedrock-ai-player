const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const ser = m.createSerializer('1.26.40')
const proto = ser.proto
function hex(b){ let s=''; for(let i=0;i<b.length;i++){ s+=(i%16===0?'\n'+String(i).padStart(3)+': ':'')+b[i].toString(16).padStart(2,'0')+' ' } return s }
function build(blockActions, flags) {
  return {
    pitch: 0, yaw: 0, position: { x: -28.7, y: 66.0, z: 258.7 },
    move_vector: { x: 0, z: 0 }, head_yaw: 0,
    input_data: flags,
    input_mode: 'mouse', play_mode: 'normal', interaction_model: 'classic',
    interact_rotation: { x: 0, z: 0 }, tick: 1, delta: { x: 0, y: 0, z: 0 },
    transaction_presence: false, transaction: null,
    item_stack_request_presence: false, item_stack_request: null,
    block_action_presence: blockActions != null,
    block_action: blockActions,
    vehicle_rotation_presence: false, vehicle_rotation: null,
    predicted_vehicle_presence: false, predicted_vehicle: null,
    analogue_move_vector: { x: 0, z: 0 }, camera_orientation: { x: 0, y: 0, z: 0 },
    raw_move_vector: { x: 0, z: 0 }
  }
}
for (const fn of ['createPacketBuffer','write']) {
  if (typeof proto[fn] !== 'function') { console.log('proto 无 ' + fn); continue }
  try {
    const r = proto[fn]('packet_player_auth_input', build([{ action:'start_break', position:{x:-29,y:65,z:258}, face:1 }], [35]))
    const b = Buffer.isBuffer(r) ? r : (r && r.data) ? r.data : null
    console.log(fn + ' -> len=' + (b ? b.length : '?') + (b ? ' hex=' + b.toString('hex').slice(0,80) : ''))
    if (b) { console.log(hex(b)); require('fs').writeFileSync('/tmp/pkt.hex', b.toString('hex')) }
    break
  } catch (e) { console.log(fn + ' ERR ' + e.message) }
}
