const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const ser = m.createSerializer('1.26.40')
console.log('ser keys: ' + Object.keys(ser).join(','))
const base = {
  pitch: 0, yaw: 0, position: { x: -28.7, y: 66.0, z: 258.7 },
  move_vector: { x: 0, z: 0 }, head_yaw: 0,
  input_data: [35],
  input_mode: 'mouse', play_mode: 'normal', interaction_model: 'classic',
  interact_rotation: { x: 0, z: 0 }, tick: 1, delta: { x: 0, y: 0, z: 0 },
  transaction_presence: false, transaction: null,
  item_stack_request_presence: false, item_stack_request: null,
  block_action_presence: true,
  block_action: [{ action: 'start_break', position: { x: -29, y: 65, z: 258 }, face: 1 }],
  vehicle_rotation_presence: false, vehicle_rotation: null,
  predicted_vehicle_presence: false, predicted_vehicle: null,
  analogue_move_vector: { x: 0, z: 0 }, camera_orientation: { x: 0, y: 0, z: 0 },
  raw_move_vector: { x: 0, z: 0 }
}
for (const n of ['player_auth_input', 'packet_player_auth_input']) {
  try { const b = ser.createPacketBuffer(n, base); console.log(n + ' -> len=' + b.length + ' hex=' + b.toString('hex')) }
  catch (e) { console.log(n + ' ERR ' + e.message) }
}
try { const b = ser.createPacketBuffer('_raw_player_auth_input', base); console.log('raw -> len=' + b.length) } catch (e) { console.log('raw ERR ' + e.message) }
