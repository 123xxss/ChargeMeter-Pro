#!/usr/bin/env bash
# PowerMeter 核心算法测试入口（不依赖 JUnit / 不依赖 Android 运行时）
#
# 用法：
#   bash tests/run_tests.sh
#   ANDROID_JAR=/root/android/sdk/android-36/android.jar bash tests/run_tests.sh
#
# 做的事：
#   1) javac 编译 src/com/dsh/powermeter/core + tests（classpath 用 android.jar）
#   2) 只加载 core+tests 类运行纯 JVM 测试（运行时不需要 android.jar：
#      BatteryManager 的常量已被 javac 内联，若核心层泄漏 Android 依赖会在此暴露）
#   3) 运行安全 / PRD 静态扫描（tests/security_scan.sh）
#
# 输出：每条用例一行 PASS / FAIL，最后打印「通过 N / 失败 M」。
# 退出码：0 = 全通过；非 0 = 有失败（可用于 CI / build.sh）。
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
BUILD="$HERE/build"
OUT="$BUILD/classes"
JAVA_LOG="$BUILD/java-tests.log"
SEC_LOG="$BUILD/static-scan.log"
MIN_CASES="${MIN_CASES:-150}"

# ---------------------------------------------------------------- 环境
SDK_ROOT="${ANDROID_SDK_ROOT:-/root/android/sdk}"
PLAT="${ANDROID_JAR:-$SDK_ROOT/android-36/android.jar}"
if [ ! -f "$PLAT" ]; then
  for cand in "$SDK_ROOT"/android-*/android.jar \
              "$SDK_ROOT"/platforms/android-*/android.jar; do
    if [ -f "$cand" ]; then PLAT="$cand"; break; fi
  done
fi
if [ ! -f "$PLAT" ]; then
  echo "FAIL 环境：找不到 android.jar（可用 ANDROID_JAR=/path/to/android.jar 指定）"
  echo "==== 结果：通过 0 / 失败 1 ===="
  exit 1
fi
if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
  echo "FAIL 环境：缺少 javac / java（需要 JDK 17+）"
  echo "==== 结果：通过 0 / 失败 1 ===="
  exit 1
fi

rm -rf "$BUILD"
mkdir -p "$OUT"

# core 的 ChargeEngine / BatteryReader 引用 App 层的 Prefs / BrandCompat（编译期符号），
# 因此把它们一起加入 javac 源文件列表。运行时不加载这两个类，测试仍只实例化纯算法类。
CORE_SRC="$ROOT/src/com/dsh/powermeter/core"
DEP_SRCS=(
  "$ROOT/src/com/dsh/powermeter/Prefs.java"
  "$ROOT/src/com/dsh/powermeter/BrandCompat.java"
)
for f in "${DEP_SRCS[@]}"; do
  if [ ! -f "$f" ]; then
    echo "FAIL 环境：缺少编译依赖 $f"
    echo "==== 结果：通过 0 / 失败 1 ===="
    exit 1
  fi
done
{
  find "$CORE_SRC" "$HERE/com" -name '*.java'
  printf '%s\n' "${DEP_SRCS[@]}"
} | sort > "$BUILD/sources.txt"

echo "PowerMeter 测试：$(wc -l < "$BUILD/sources.txt") 个源文件（core + 2 个编译期依赖 + tests）"
echo "android.jar：$PLAT"
echo

# ---------------------------------------------------------------- 1/3 编译
echo "== 1/3 编译 src/com/dsh/powermeter/core + Prefs/BrandCompat + tests"
if ! javac -nowarn -encoding UTF-8 -source 17 -target 17 \
        -classpath "$PLAT" -d "$OUT" @"$BUILD/sources.txt" >"$BUILD/javac.log" 2>&1; then
  echo "FAIL 编译：javac 失败"
  sed 's/^/    /' "$BUILD/javac.log" | tail -40
  echo
  echo "==== 结果：通过 0 / 失败 1 ===="
  exit 1
fi
echo "   编译通过 → $OUT"

# ---------------------------------------------------------------- 2/3 单元测试
echo
echo "== 2/3 运行核心算法测试（纯 JVM：运行时 classpath 仅 core+tests）"
java -cp "$OUT" com.dsh.powermeter.qa.Runner >"$JAVA_LOG" 2>&1
JAVA_RC=$?
cat "$JAVA_LOG"

# ---------------------------------------------------------------- 3/3 静态扫描
echo
echo "== 3/3 安全 / PRD 静态扫描"
if bash "$HERE/security_scan.sh" "$ROOT" >"$SEC_LOG" 2>&1; then
  SEC_RC=0
else
  SEC_RC=$?
fi
cat "$SEC_LOG"

# ---------------------------------------------------------------- 汇总
count_lines() {
  local file="$1" prefix="$2" n
  if [ ! -f "$file" ]; then
    echo 0
    return
  fi
  n="$(grep -c "^${prefix} " "$file" 2>/dev/null)"
  echo "${n:-0}"
}

JAVA_PASS="$(count_lines "$JAVA_LOG" PASS)"
JAVA_FAIL="$(count_lines "$JAVA_LOG" FAIL)"
SEC_PASS="$(count_lines "$SEC_LOG" PASS)"
SEC_FAIL="$(count_lines "$SEC_LOG" FAIL)"

TOTAL_PASS=$((JAVA_PASS + SEC_PASS))
TOTAL_FAIL=$((JAVA_FAIL + SEC_FAIL))

if [ "$JAVA_RC" -ne 0 ] && [ "$JAVA_FAIL" -eq 0 ]; then
  echo
  echo "FAIL 测试进程异常退出（rc=$JAVA_RC，却没有 FAIL 明细，可能是崩溃 / 类加载失败）"
  TOTAL_FAIL=$((TOTAL_FAIL + 1))
fi
if [ "$SEC_RC" -ne 0 ] && [ "$SEC_FAIL" -eq 0 ]; then
  echo
  echo "FAIL 静态扫描进程异常退出（rc=$SEC_RC，却没有 FAIL 明细）"
  TOTAL_FAIL=$((TOTAL_FAIL + 1))
fi
if [ $((TOTAL_PASS + TOTAL_FAIL)) -lt "$MIN_CASES" ]; then
  echo
  echo "FAIL 用例数下限：期望至少 $MIN_CASES 条，实际只有 $((TOTAL_PASS + TOTAL_FAIL)) 条（测试被删或空跑？）"
  TOTAL_FAIL=$((TOTAL_FAIL + 1))
fi

echo
echo "=================================================================="
echo "单元测试：通过 $JAVA_PASS / 失败 $JAVA_FAIL"
echo "静态扫描：通过 $SEC_PASS / 失败 $SEC_FAIL"
echo "==== 结果：通过 $TOTAL_PASS / 失败 $TOTAL_FAIL ===="
echo "运行时仅加载 core+tests 类（未使用 android.jar），失败时退出码非 0"

if [ "$TOTAL_FAIL" -eq 0 ] && [ "$JAVA_RC" -eq 0 ] && [ "$SEC_RC" -eq 0 ]; then
  exit 0
fi
exit 1
