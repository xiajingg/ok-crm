#!/usr/bin/env bash
#
# 本地一键启动：后端 + 前端
#
# 用法：
#   ./scripts/start-local.sh mysql    # 连真实 MySQL（需要 DB_PASSWORD，见下）
#   ./scripts/start-local.sh demo     # 零依赖模式（嵌入式 H2，不需要 MySQL）
#
# 数据库密码的来源（按顺序查找）：
#   1. 已导出的环境变量 DB_PASSWORD
#   2. 仓库根目录的 .env.local（该文件已被 .gitignore 忽略，不会进版本库）
#
# 其它可用环境变量：
#   BACKEND_PORT  后端端口，默认 18080（8080 常被其它程序占用）
#   FRONTEND_PORT 前端端口，默认 5173
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-mysql}"
BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
JAR="$ROOT/apps/crm-boot/target/ok-crm.jar"

# ---------- 载入 .env.local ----------
if [ -f "$ROOT/.env.local" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env.local"
  set +a
fi

# ---------- 前置检查 ----------
if [ ! -f "$JAR" ]; then
  echo "❌ 找不到 $JAR"
  echo "   先打包：mvn -B -DskipTests package -pl apps/crm-boot -am"
  exit 1
fi

if [ ! -d "$ROOT/web/node_modules" ]; then
  echo "❌ 前端依赖未安装"
  echo "   先安装：cd web && npm install"
  exit 1
fi

if lsof -ti:"$BACKEND_PORT" >/dev/null 2>&1; then
  echo "❌ 端口 $BACKEND_PORT 已被占用，占用进程："
  lsof -i:"$BACKEND_PORT" | tail -n +2
  echo "   换端口：BACKEND_PORT=18081 $0 $MODE"
  exit 1
fi

# ---------- 组装后端启动参数 ----------
case "$MODE" in
  mysql)
    if [ -z "${DB_PASSWORD:-}" ]; then
      echo "❌ 未提供数据库密码。二选一："
      echo "   export DB_PASSWORD=你的MySQL密码 && $0 mysql"
      echo "   或在 $ROOT/.env.local 里写一行：DB_PASSWORD=你的MySQL密码"
      exit 1
    fi
    PROFILE=dev
    ;;
  demo)
    PROFILE=demo
    ;;
  *)
    echo "❌ 未知模式：${MODE}（可选：mysql / demo）"
    exit 1
    ;;
esac

# ---------- 启动 ----------
BACKEND_PID=""
cleanup() {
  if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    # 注意：变量一定要用 ${} 包起来。紧跟在变量后面的全角括号是多字节字符，
    # 写成 $BACKEND_PID） 会被 bash 把多字节字符的字节并进变量名，报 unbound variable。
    echo "正在停止后端（pid=${BACKEND_PID}）..."
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

echo "▶ 后端：profile=$PROFILE  端口=$BACKEND_PORT"
java -jar "$JAR" --spring.profiles.active="$PROFILE" --server.port="$BACKEND_PORT" &
BACKEND_PID=$!

# 等后端就绪
echo -n "  等待后端就绪"
for _ in $(seq 1 60); do
  if curl -fsS -m 2 "http://127.0.0.1:$BACKEND_PORT/api/actuator/health" >/dev/null 2>&1; then
    echo " ✅"
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    echo "❌ 后端进程已退出，请检查上面的日志"
    exit 1
  fi
  echo -n "."
  sleep 1
done

echo ""
echo "──────────────────────────────────────────────"
echo " 管理后台   http://localhost:${FRONTEND_PORT}"
echo " 接口文档   http://127.0.0.1:${BACKEND_PORT}/api/swagger-ui.html"
echo " 默认账号   admin / admin123456"
if [ "$PROFILE" = "mysql" ]; then
  echo " 数据库     MySQL ok_crm（${DB_HOST:-127.0.0.1}:${DB_PORT:-3306}）"
else
  echo " 数据库     嵌入式 H2（./data/，重启不丢）"
fi
echo ""
echo " ⚠ 访问后台请用 localhost，不要用 127.0.0.1 ——"
echo "   Vite 默认只监听 IPv6 的 localhost。"
echo ""
echo " 首次使用：先用「平台管理端」登录开通企业，再用「企业登录」进去"
echo " 按 Ctrl-C 停止（后端会一起停）"
echo "──────────────────────────────────────────────"
echo ""

cd "$ROOT/web"
VITE_API_TARGET="http://127.0.0.1:$BACKEND_PORT" npm run dev -- --port "$FRONTEND_PORT"
