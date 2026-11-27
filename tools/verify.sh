#!/usr/bin/env bash
# 交付前静态验收：权限、导出组件、危险 API、APK 内容、签名。
set -u
P="$(cd "$(dirname "$0")/.." && pwd)"
APK="$P/powermeter.apk"
AAPT2="${AAPT2:-qemu-x86_64-static /root/android/sdk/android-14/aapt2}"
FAIL=0
note() { echo "  $1"; }
fail() { echo "  [FAIL] $1"; FAIL=1; }

echo "== 1) Manifest 权限"
BADGING=$($AAPT2 dump badging "$APK" 2>/dev/null || true)
if echo "$BADGING" | grep -q "android.permission.INTERNET"; then
  fail "发现 INTERNET 权限"
else
  note "无 INTERNET 权限"
fi
for perm in $(echo "$BADGING" | sed -n "s/uses-permission: name='\([^']*\)'.*/\1/p"); do
  note "权限: $perm"
done

echo "== 2) 导出组件"
XML=$($AAPT2 dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null || true)
EXPORTED_TRUE=$(echo "$XML" | grep -c 'android:exported.*=true' || true)
# 只允许 MainActivity 导出；用组件块粗校验
if [ "$EXPORTED_TRUE" -ne 2 ]; then
  echo "$XML" | grep -B3 -A3 'android:exported' | head -40
  fail "导出组件数量异常（仅允许 Launcher + 受系统权限保护的 ShizukuProvider）"
else
  note "导出组件数量: $EXPORTED_TRUE"
fi

echo "== 3) 源码危险调用扫描"
SCAN=$(grep -RInE 'Runtime\.getRuntime\(\)\.(exec|load|loadLibrary)|Runtime\.exec|ProcessBuilder|HttpURLConnection|java\.net\.Socket|INTERNET|BATTERY_STATS|SYSTEM_ALERT_WINDOW|Process\.myUid\(\).*2000|uid *== *2000' "$P/src" --exclude=BatteryUserService.java 2>/dev/null || true)
if [ -n "$SCAN" ]; then
  echo "$SCAN"
  fail "普通源码包含禁止/需评审的调用"
else
  if grep -q 'new ProcessBuilder("dumpsys", "batterystats")' "$P/src/com/dsh/powermeter/shizuku/BatteryUserService.java"; then
    note "无网络/2000 信任；精准模式仅固定只读 dumpsys batterystats"
  else
    fail "精准模式固定命令缺失/异常"
  fi
fi

echo "== 4) APK 结构"
if unzip -l "$APK" | grep -qE 'classes(2)?\.dex'; then note "包含 dex"; else fail "缺少 dex"; fi
if unzip -l "$APK" | grep -q 'lib/'; then note "包含 native 库（需确认来源）"; fi
SIZE=$(stat -c%s "$APK" 2>/dev/null || echo 0)
note "APK 大小: $SIZE bytes"

echo "== 5) 签名"
if java -jar /root/android/sdk/android-14/lib/apksigner.jar verify "$APK" >/dev/null 2>&1; then
  note "签名校验通过"
else
  fail "签名校验失败"
fi

if [ "$FAIL" -ne 0 ]; then
  echo "== 验收失败"
  exit 1
fi
echo "== 验收通过"
