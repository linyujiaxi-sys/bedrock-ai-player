#!/bin/bash
# ============================================================================
#  MC 基岩版「真 AI 玩家」一键启动脚本  v1.0
# ============================================================================
#  用法：
#    bash start.sh            # 启动服务器 + 机器人（DeepSeek）
#    bash start.sh stop       # 全部停止
#    bash start.sh restart    # 重启
#    bash start.sh status     # 查看状态
#    bash start.sh logs       # 实时看两边日志（Ctrl+C 退出）
#
#  这个脚本存在的意义：
#    以前每次重启要敲 8 条命令、4 个易错点（忘建 FIFO / 漏 TLS 参数 /
#    忘限内存 / 死等 20 秒）。任何一条漏了，就是"上不去服务器呜呜呜"。
#    现在一条命令搞定。
#
#  ⚠️ 换机器只需改下面 CONFIG 区。
#  ⚠️ 不要用 `set -e`：会让 Ubuntu 终端会话直接退出卡死。
# ============================================================================

# ------------------------------- CONFIG ------------------------------------
SRV_DIR=/tmp/nukkit_server2            # 服务器目录
JAR=nukkit.jar                          # 服务器主 jar
SRV_LOG=/tmp/nukkit_server2.log         # 服务器日志
CMD_FIFO=/tmp/nukkit_cmd                # 服务器控制台输入口（发指令用）
MC_CMD_FIFO=/tmp/mc_cmd                 # 机器人指令口
MC_DIR=/tmp                             # 机器人工作目录（必须能 resolve bedrock-protocol）
MC_JS=$MC_DIR/mcact.js                  # 机器人脚本
MC_LOG=/tmp/mcact.log                   # 机器人日志
MEM=512M                                # 服务器内存上限（★ 手机别调大，会 OOM）
PORT=19132                              # 服务器端口（UDP）
WAIT_MAX=120                            # 等服务器启动最长秒数（实测约 20s）
DONE_KEY="启动完成"                       # 服务器启动完成的日志关键词
# ---------------------------------------------------------------------------

C_G="\033[32m"; C_R="\033[31m"; C_Y="\033[33m"; C_N="\033[0m"
log()  { echo -e "${C_Y}[start.sh]${C_N} $*"; }
ok()   { echo -e "${C_G}  ✅ $*${C_N}"; }
bad()  { echo -e "${C_R}  ❌ $*${C_N}"; }

is_srv_running() { pgrep -f "nukkit.jar" >/dev/null 2>&1; }
is_bot_running() { pgrep -f "mcact.js"  >/dev/null 2>&1; }
srv_pid() { pgrep -f "nukkit.jar" 2>/dev/null | tr '\n' ' '; }
bot_pid() { pgrep -f "mcact.js"  2>/dev/null | tr '\n' ' '; }
# 注意：本环境没有 ip / ifconfig，只有 hostname -I 可用
# hostname -I 会返回多个地址（含 VPN/临时接口），优先选真正的家用局域网段
local_ip() {
  local all ip
  all=$(hostname -I 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$')
  ip=$(echo "$all" | grep -E '^192\.168\.' | head -1)
  [ -z "$ip" ] && ip=$(echo "$all" | grep -E '^10\.' | head -1)
  [ -z "$ip" ] && ip=$(echo "$all" | head -1)
  echo "$ip"
}

# ---------------------------------------------------------------------------
# 等服务器启动完成（轮询日志，而不是死等 20 秒）
#   $1 = 启动前日志的行数（避免把上一次的"启动完成"当成这次）
# ---------------------------------------------------------------------------
wait_server() {
  local base="$1" i
  printf "    等待启动"
  for ((i=0; i<WAIT_MAX; i++)); do
    # 进程如果先死了，立刻报错（不用等满 120 秒）
    if ! is_srv_running; then
      printf "\n"
      bad "服务器进程退出了！日志末尾："
      tail -20 "$SRV_LOG" 2>/dev/null
      return 1
    fi
    # 只检查本次新增的日志行
    if tail -n +$((base+1)) "$SRV_LOG" 2>/dev/null | grep -aq "$DONE_KEY"; then
      printf "\n"
      return 0
    fi
    printf "."
    sleep 1
  done
  printf "\n"
  bad "等待超时（${WAIT_MAX}s），服务器可能卡住了。看：tail -30 $SRV_LOG"
  return 1
}

# ---------------------------------------------------------------------------
# 启动服务器
# ---------------------------------------------------------------------------
start_server() {
  log "① 启动服务器（内存上限 $MEM）..."

  # FIFO 必须存在，否则控制台指令发不进去
  [ -p "$CMD_FIFO" ] || { rm -f "$CMD_FIFO"; mkfifo -m 666 "$CMD_FIFO"; }
  [ -p "$MC_CMD_FIFO" ] || { rm -f "$MC_CMD_FIFO"; mkfifo -m 666 "$MC_CMD_FIFO"; }

  # 日志太大就轮转（防止 ~/.log 无限膨胀）
  if [ -f "$SRV_LOG" ] && [ "$(stat -c%s "$SRV_LOG" 2>/dev/null || echo 0)" -gt 5000000 ]; then
    mv -f "$SRV_LOG" "$SRV_LOG.1" 2>/dev/null
    log "    日志已轮转 -> ${SRV_LOG}.1"
  fi
  # 保证日志文件存在（轮转后 / 首次启动时），否则下面的行数统计会报错
  [ -f "$SRV_LOG" ] || : > "$SRV_LOG"

  local base
  base=$(wc -l < "$SRV_LOG" 2>/dev/null || echo 0)

  cd "$SRV_DIR" || { bad "进不去 $SRV_DIR"; return 1; }

  # ★ 全套参数一个都不能少：
  #   -Xmx512M             手机内存上限
  #   -D...preferIPv4Stack=true 强制 IPv4（微软 JWKS 走 IPv6 会 Connection reset）
  #   -Dhttps.protocols / -Djdk.tls.client.protocols  TLS1.2（避开 TLS1.3 被 Azure 重置）
  nohup sh -c "tail -f $CMD_FIFO | java -Xmx$MEM \
      -Djava.net.preferIPv4Stack=true \
      -Dhttps.protocols=TLSv1.2,TLSv1.3 \
      -Djdk.tls.client.protocols=TLSv1.2 \
      -jar $JAR --no-wizard" >> "$SRV_LOG" 2>&1 &

  wait_server "$base" || return 1
  ok "服务器就绪（PID $(srv_pid)）"
  return 0
}

# ---------------------------------------------------------------------------
# 启动机器人
# ---------------------------------------------------------------------------
start_bot() {
  log "② 启动机器人 DeepSeek..."
  cd "$MC_DIR" || { bad "进不去 $MC_DIR"; return 1; }
  nohup node "$MC_JS" > "$MC_LOG" 2>&1 &
  sleep 3
  if is_bot_running; then
    ok "机器人就绪（PID $(bot_pid)）"
    return 0
  else
    bad "机器人没起来！看：tail -30 $MC_LOG"
    return 1
  fi
}

# ---------------------------------------------------------------------------
# 优雅停止
# ---------------------------------------------------------------------------
stop_all() {
  log "停止中..."

  # 1) 先给服务器发 stop，让它自己保存世界（重要！别直接 kill）
  if is_srv_running; then
    log "    向服务器发送 stop（保存世界）..."
    timeout 5 bash -c "printf 'stop\n' > '$CMD_FIFO'" 2>/dev/null
    local i
    for ((i=0; i<30; i++)); do
      is_srv_running || break
      sleep 1
    done
    if is_srv_running; then
      bad "优雅停止超时，强制 kill"
      pkill -f "nukkit.jar" 2>/dev/null
    else
      ok "服务器已停止"
    fi
  else
    log "    服务器本来就没跑"
  fi

  # 2) 停机器人
  if is_bot_running; then
    pkill -f "mcact.js" 2>/dev/null
    ok "机器人已停止"
  fi

  # 3) 清理 FIFO 的 tail -f 残留进程（命令行不含 nukkit.jar，上一步杀不掉）
  pkill -f "tail -f $CMD_FIFO" 2>/dev/null

  log "完成。"
}

# ---------------------------------------------------------------------------
# 状态
# ---------------------------------------------------------------------------
status() {
  echo "====================== 状态 ======================"
  if is_srv_running; then ok "服务器  运行中   PID: $(srv_pid)"; else bad "服务器  未运行"; fi
  if is_bot_running; then ok "机器人  运行中   PID: $(bot_pid)"; else bad "机器人  未运行"; fi
  echo "  端口     : $PORT (UDP)"
  echo "  内网地址 : $(local_ip):$PORT"
  echo "=================================================="
  if is_srv_running && is_bot_running; then
    echo -e "${C_G}  可以进游戏了！${C_N}"
  fi
}

# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
do_start() {
  if is_srv_running || is_bot_running; then
    bad "已有进程在跑 —— 用 restart 更安全：bash start.sh restart"
    status
    return 0
  fi
  start_server && start_bot || { bad "启动失败，已中止"; return 1; }
  echo
  echo -e "${C_G}🎉 全部就绪！${C_N}"
  status
}

case "${1:-start}" in
  start)   do_start ;;
  stop)    stop_all ;;
  restart) stop_all; sleep 2; do_start ;;
  status)  status ;;
  logs)    log "实时日志（Ctrl+C 退出）..."; tail -f "$SRV_LOG" "$MC_LOG" ;;
  *)       echo "用法: bash start.sh [start|stop|restart|status|logs]" ;;
esac
