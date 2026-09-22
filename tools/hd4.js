const m = require('/tmp/node_modules/bedrock-protocol/src/transforms/serializer')
const ser = m.createSerializer('1.26.40').proto
const pkt = {
  pitch:0,yaw:0,position:{x:-28.7,y:66,z:258.7},move_vector:{x:0,z:0},head_yaw:0,
  input_data:[35],input_mode:'mouse',play_mode:'normal',interaction_model:'classic',
  interact_rotation:{x:0,z:0},tick:1,delta:{x:0,y:0,z:0},
  transaction_presence:false,item_stack_request_presence:false,
  block_action_presence:true,_bh:true,
  block_action:[{action:'start_break',position:{x:-29,y:65,z:258},face:1}],
  vehicle_rotation_presence:false,predicted_vehicle_presence:false,
  analogue_move_vector:{x:0,z:0},camera_orientation:{x:0,y:0,z:0},raw_move_vector:{x:0,z:0}
}
const b = ser.createPacketBuffer('packet_player_auth_input', pkt)
console.log('LEN='+b.length)
require('fs').writeFileSync('/tmp/pkt.hex', b.toString('hex'))
