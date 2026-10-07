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
#   BACKEND_PORT  后端端口，默认 9001（注意：本机 9000/9001 可能被 rustfs 占用，
#                 被占用时脚本会直接报出来，换一个即可，例如 BACKEND_PORT=9002）
#   FRONTEND_PORT 前端端口，默认 5173
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-mysql}"
BACKEND_PORT="${BACKEND_PORT:-9001}"
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
  echo "   先打包：./scripts/package.sh"
  exit 1
fi

# ---------- 选择 Java：本项目编译目标是 Java 21 ----------
# 不能直接用裸 java：多版本管理工具（conda、jenv 等）常把 JAVA_HOME 指到旧版本，
# 用旧版本启动会报 UnsupportedClassVersionError（class 65.0 vs 61.0），而且报错信息很难懂。
REQUIRED_JAVA_MAJOR=21

java_major_version() {
  "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

pick_java_bin() {
  # 1) JAVA_HOME 里的版本够新就用它
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    if [ "$(java_major_version "$JAVA_HOME/bin/java")" -ge "$REQUIRED_JAVA_MAJOR" ] 2>/dev/null; then
      echo "$JAVA_HOME/bin/java"
      return 0
    fi
  fi

  # 2) 用 macOS 自带工具找：先精确匹配编译目标版本，再放宽到 21+
  if [ -x /usr/libexec/java_home ]; then
    for spec in "$REQUIRED_JAVA_MAJOR" "${REQUIRED_JAVA_MAJOR}+"; do
      home="$(/usr/libexec/java_home -v "$spec" 2>/dev/null || true)"
      if [ -n "$home" ] && [ -x "$home/bin/java" ]; then
        echo "$home/bin/java"
        return 0
      fi
    done
  fi

  # 3) 兜底：扫一遍常见安装目录
  find /Library/Java/JavaVirtualMachines "$HOME/Library/Java/JavaVirtualMachines" \
       -maxdepth 3 -name Home -type d 2>/dev/null | while IFS= read -r home; do
    [ -x "$home/bin/java" ] || continue
    if [ "$(java_major_version "$home/bin/java")" -ge "$REQUIRED_JAVA_MAJOR" ] 2>/dev/null; then
      echo "$home/bin/java"
      return 0
    fi
  done

  return 1
}

JAVA_BIN="$(pick_java_bin || true)"
if [ -z "$JAVA_BIN" ]; then
  echo "❌ 找不到 Java ${REQUIRED_JAVA_MAJOR} 或更高版本，无法启动"
  echo ""
  echo "   当前 PATH 上的 java ：$(java -version 2>&1 | head -1)"
  echo "   当前 JAVA_HOME      ：${JAVA_HOME:-（未设置）}"
  echo ""
  echo "   本项目编译目标是 Java ${REQUIRED_JAVA_MAJOR}。用低版本启动会报："
  echo "   UnsupportedClassVersionError（class file version 65.0 vs 61.0）"
  echo ""
  echo "   本机已安装的 JDK："
  /usr/libexec/java_home -V 2>&1 | sed 's/^/     /' | head -8
  echo ""
  echo "   解决办法（任选其一）："
  echo "     export JAVA_HOME=\$(/usr/libexec/java_home -v ${REQUIRED_JAVA_MAJOR})"
  echo "     JAVA_HOME=/path/to/jdk-21 $0 $MODE"
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
  echo "   换端口：BACKEND_PORT=9002 $0 $MODE"
  echo "   或先停掉占用者：kill \$(lsof -ti:$BACKEND_PORT)"
  exit 1
fi

# ---------- 把 jar 复制出来再运行（重要，别删） ----------
# 背景：Spring Boot 可执行 jar 是「jar 套 jar」。JVM 启动时缓存外层 jar 的中央目录
# （类名 -> 字节偏移），内层 jar（比如 logback-classic）按需懒加载。
# 如果构建产物在应用运行期间被 mvn package **原地重写**（inode 不变、内容已变），
# JVM 会拿着旧索引去新文件里读，于是懒加载的类突然「找不到」：
#     NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy
# 症状很有迷惑性：平时不报，一有异常日志就炸 —— 因为 ThrowableProxy 只在
# 记录异常堆栈时才会被加载，是重写之后第一个被懒加载的类。
#
# 对策：运行时用一份独立副本，构建产物怎么改都影响不到正在跑的实例。
# 副本按端口命名，保证同时跑多个实例时互不覆盖。
RUN_DIR="$ROOT/apps/crm-boot/target/run"
RUN_JAR="$RUN_DIR/ok-crm-${BACKEND_PORT}.jar"
mkdir -p "$RUN_DIR"
cp -f "$JAR" "$RUN_JAR"

# ---------- 组装后端启动参数 ----------
# 用字符串而不是数组：bash 3.2（macOS 自带）在 set -u 下展开空数组会报 unbound variable
EXTRA_ARGS=""
CACHE_MODE=""

case "$MODE" in
  mysql)
    if [ -z "${DB_PASSWORD:-}" ]; then
      echo "❌ 未提供数据库密码。二选一："
      echo "   export DB_PASSWORD=你的MySQL密码 && $0 mysql"
      echo "   或在 $ROOT/.env.local 里写一行：DB_PASSWORD=你的MySQL密码"
      exit 1
    fi
    PROFILE=dev

    # 检测到 Redis 就用 Redis 缓存 + Redis 分布式锁（更接近生产）；
    # 检测不到就退回进程内实现，功能一致，只是不支持多实例。
    REDIS_PORT="${REDIS_PORT:-6379}"
    if nc -z -w 1 127.0.0.1 "$REDIS_PORT" >/dev/null 2>&1; then
      CACHE_MODE="Redis（127.0.0.1:${REDIS_PORT}）"
      EXTRA_ARGS="--spring.cache.type=redis --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=${REDIS_PORT}"
    else
      CACHE_MODE="进程内（未检测到 Redis，不影响功能）"
    fi
    ;;
  demo)
    PROFILE=demo
    CACHE_MODE="进程内"
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
  # 顺手清掉运行时副本，别让 target/ 里堆一堆几十 MB 的 jar
  [ -n "${RUN_JAR:-}" ] && rm -f "$RUN_JAR"
}
trap cleanup EXIT INT TERM

echo "▶ 后端：profile=${PROFILE}  端口=${BACKEND_PORT}  缓存=${CACHE_MODE}"
echo "  Java：${JAVA_BIN}"
echo "  运行包：${RUN_JAR}（构建产物的副本，打包不会影响它）"
# shellcheck disable=SC2086
"$JAVA_BIN" -jar "$RUN_JAR" --spring.profiles.active="$PROFILE" --server.port="$BACKEND_PORT" $EXTRA_ARGS &
BACKEND_PID=$!

# 等后端就绪
echo -n "  等待后端就绪"
BACKEND_READY=0
for _ in $(seq 1 60); do
  # 两个坑，缺一个探测就形同虚设：
  #   1) 不能只看 HTTP 状态码 —— 兜底异常处理器对「接口不存在」也返回 HTTP 200，
  #      body 里才是 {"code":404,...}。必须校验响应体里有 "status":"UP"。
  #   2) 本机可能配了 http_proxy（开发机常见），localhost 请求必须 --noproxy 绕过，
  #      否则请求被代理吞掉，表现为一直超时。
  if curl -fsS -m 2 --noproxy '*' "http://127.0.0.1:$BACKEND_PORT/api/actuator/health" 2>/dev/null \
       | grep -q '"status":"UP"'; then
    echo " ✅"
    BACKEND_READY=1
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo ""
    echo "❌ 后端进程已退出，请看上方的启动日志"
    exit 1
  fi
  echo -n "."
  sleep 1
done

if [ "$BACKEND_READY" -ne 1 ]; then
  echo ""
  echo "❌ 等待 60 秒后端仍未就绪，请看上方的启动日志"
  exit 1
fi

echo ""
echo "──────────────────────────────────────────────"
echo " 管理后台   http://localhost:${FRONTEND_PORT}"
echo " 接口文档   http://127.0.0.1:${BACKEND_PORT}/api/swagger-ui.html"
echo " 默认账号   admin / admin123456"
# 注意判断的是 MODE 而不是 PROFILE：mysql 模式下 profile 用的是 dev
if [ "$MODE" = "mysql" ]; then
  echo " 数据库     MySQL ok_crm（${DB_HOST:-127.0.0.1}:${DB_PORT:-3306}）"
else
  echo " 数据库     嵌入式 H2（./data/，重启不丢）"
fi
echo " 缓存       ${CACHE_MODE}"
echo ""
echo " ⚠ 访问后台请用 localhost，不要用 127.0.0.1 ——"
echo "   Vite 默认只监听 IPv6 的 localhost。"
echo ""
echo " 按 Ctrl-C 停止（后端会一起停）"
echo " 单独停后端：kill \$(lsof -ti:${BACKEND_PORT})"
echo "──────────────────────────────────────────────"
echo ""

cd "$ROOT/web"
VITE_API_TARGET="http://127.0.0.1:$BACKEND_PORT" npm run dev -- --port "$FRONTEND_PORT"
