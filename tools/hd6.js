const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const proto = m.createSerializer('1.26.40').proto
const slot = (cid, s) => ({ slot_type: { container_id: cid, dynamic_container_id: null }, slot: s, stack_id: 0 })
const pkt = {
  pitch:0,yaw:0,position:{x:-28.7,y:66,z:258.7},move_vector:{x:0,z:0},head_yaw:0,
  input_data:[35,36],
  input_mode:'mouse',play_mode:'normal',interaction_model:'classic',
  interact_rotation:{x:0,z:0},tick:5,delta:{x:0,y:0,z:0},
  transaction_presence:false,
  item_stack_request_presence:true,
  item_stack_request:{
    request_id:1,
    actions:[{ type_id:'take', legacy_type_id:0, count:1,
      source: slot('hotbar_and_inventory',0),
      destination: slot('hotbar_and_inventory',5) }],
    custom_names: [],
    cause: 0
  },
  block_action_presence:false,
  vehicle_rotation_presence:false,predicted_vehicle_presence:false,
  analogue_move_vector:{x:0,z:0},camera_orientation:{x:0,y:0,z:0},raw_move_vector:{x:0,z:0}
}
try {
  const b = proto.createPacketBuffer('packet_player_auth_input', pkt)
  console.log('LEN='+b.length)
  require('fs').writeFileSync('/tmp/pkt.hex', b.toString('hex'))
} catch (e) { console.log('ERR '+e.message) }
