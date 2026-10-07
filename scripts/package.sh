#!/usr/bin/env bash
#
# 安全打包：先确认构建产物没被运行中的进程占用，再执行 mvn package。
#
# 为什么需要它：
#   Spring Boot 可执行 jar 是「jar 套 jar」。JVM 启动时缓存外层 jar 的中央目录
#   （类名 -> 字节偏移），内层 jar（logback 等）按需懒加载。若 mvn package 在应用
#   运行期间**原地重写**了同一个 jar（inode 不变、内容已变），JVM 会拿着旧索引去
#   新文件里读，于是懒加载的类突然「找不到」：
#       NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy
#   症状极具迷惑性：平时不报，一有异常日志就炸 —— 因为 ThrowableProxy 只在记录
#   异常堆栈时才加载，正好是重写之后第一个被懒加载的类。
#
# 用法：
#   ./scripts/package.sh                # 跳过测试，快速打包
#   ./scripts/package.sh -DskipTests=false   # 带上测试（ArchUnit 模块边界检查）
#   ./scripts/package.sh -o              # 即使 jar 被占用也强行打包（不推荐）
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/apps/crm-boot/target/ok-crm.jar"

FORCE=0
MVN_ARGS=()
for arg in "$@"; do
  case "$arg" in
    -o|--force) FORCE=1 ;;
    *) MVN_ARGS+=("$arg") ;;
  esac
done

# ---------- 守卫：jar 是否正被进程打开 ----------
if [ "$FORCE" -eq 0 ] && [ -f "$JAR" ]; then
  HOLDERS="$(lsof -t "$JAR" 2>/dev/null || true)"
  if [ -n "$HOLDERS" ]; then
    echo "❌ 拒绝打包：$JAR 正被以下进程占用"
    echo ""
    # shellcheck disable=SC2086
    for pid in $HOLDERS; do
      # 不用 ps：某些受限环境（沙箱 / 某些 MDM 策略）会拒绝执行 ps。
      # lsof 的输出第二行第一列就是命令名，够用了。
      CMD="$(lsof -p "$pid" 2>/dev/null | awk 'NR==2 {print $1}')"
      echo "   PID $pid  ${CMD:-（未知进程）}"
    done
    echo ""
    echo "   如果现在打包，运行中的实例会在下次记录异常时崩掉，报："
    echo "   NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy"
    echo ""
    echo "   处理方式（任选其一）："
    echo "     1) 先停服务再打包：kill \$(lsof -ti:9002)  然后重新运行本脚本"
    echo "     2) 用 ./scripts/start-local.sh 启动（它跑的是 jar 副本，与构建产物解耦，"
    echo "        此时本守卫不会拦你，也不需要拦）"
    echo "     3) 确实要强行打包：./scripts/package.sh -o"
    echo ""
    exit 1
  fi
fi

# ---------- 定位 mvn ----------
# 不写死裸 mvn：它可能是 homebrew keg、sdkman、IDE 内置版本，也可能只有 mvnw。
# 本机实测：PATH 里的 mvn 是个 alias（mvn-or-mvnw），在非交互 shell 里取不到，
# 直接调裸 mvn 会报 command not found。所以按可用性依次尝试。
pick_mvn() {
  if command -v mvn >/dev/null 2>&1; then echo "mvn"; return 0; fi
  if [ -x "$ROOT/mvnw" ]; then echo "$ROOT/mvnw"; return 0; fi
  for candidate in "$HOME"/.workbuddy-ai/binaries/maven/*/bin/mvn \
                   /opt/homebrew/bin/mvn /usr/local/bin/mvn; do
    if [ -x "$candidate" ]; then echo "$candidate"; return 0; fi
  done
  return 1
}

MVN="$(pick_mvn || true)"
if [ -z "$MVN" ]; then
  echo "❌ 找不到 mvn，也没有 mvnw"
  echo "   装一个：brew install maven"
  exit 1
fi

# ---------- 打包 ----------
cd "$ROOT"
echo "▶ 使用 Maven：$MVN"
echo "▶ 开始打包：-B -DskipTests package -pl apps/crm-boot -am ${MVN_ARGS[*]:-}"
# ⚠ 空数组在 bash 3.2（macOS 自带）+ set -u 下直接展开会报 unbound variable。
#   必须用 ${ARR[@]+"${ARR[@]}"} 这个写法：只在数组已定义时才展开。
"$MVN" -B -DskipTests package -pl apps/crm-boot -am ${MVN_ARGS[@]+"${MVN_ARGS[@]}"}

# ---------- 结果自检 ----------
if [ ! -f "$JAR" ]; then
  echo "❌ 打包结束但找不到 $JAR"
  exit 1
fi

SIZE_MB=$(( $(stat -f%z "$JAR") / 1048576 ))
echo ""
# ⚠ 变量必须用 ${} 包起来。紧跟其后的全角括号（是多字节字符）会被 bash
# 当成变量名的一部分，报 `JAR（: unbound variable`，而且脚本会以非 0 退出。
echo "✅ 打包完成：${JAR}（${SIZE_MB} MB）"
echo "   启动：BACKEND_PORT=9002 ./scripts/start-local.sh mysql"
