#!/usr/bin/env bash
# PowerMeter 构建脚本（不依赖 Gradle）
#
# 依赖：
#   - JDK 17+
#   - Android build-tools（aapt2 / zipalign / d8 / apksigner / aidl）
#   - Android platform android.jar
#   - ARM 主机（手机 proot）上用 qemu 运行 x86_64 工具
#
# 环境变量：
#   ANDROID_SDK_ROOT  SDK 根目录，默认 /root/android/sdk
#   BUILD_TOOLS_DIR   build-tools 目录，默认 $ANDROID_SDK_ROOT/android-14
#   ANDROID_JAR       平台 android.jar，默认 $ANDROID_SDK_ROOT/android-36/android.jar
#   QEMU              aapt2/zipalign 运行前缀，ARM 上默认 qemu-x86_64-static
#   VERSION_CODE / VERSION_NAME 版本号，默认 1 / 1.0.0
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-/root/android/sdk}"
BT="${BUILD_TOOLS_DIR:-$SDK/android-14}"
PLAT="${ANDROID_JAR:-$SDK/android-36/android.jar}"
Q="${QEMU:-}"
if [ -z "$Q" ] && [ "$(uname -m)" != "x86_64" ]; then
  if command -v qemu-x86_64-static >/dev/null 2>&1; then Q="qemu-x86_64-static"; fi
fi
VERSION_CODE="${VERSION_CODE:-15}"
VERSION_NAME="${VERSION_NAME:-1.7.1}"

P="$(cd "$(dirname "$0")" && pwd)"
OUT="$P/build"
LIBS="$P/libs"
KS="$P/signing.keystore"
ALIAS="${KEY_ALIAS:-powermeter}"
APK="$P/powermeter.apk"

for f in "$BT/aapt2" "$BT/zipalign" "$BT/lib/d8.jar" "$BT/lib/apksigner.jar" "$PLAT"; do
  [ -e "$f" ] || { echo "缺少构建依赖: $f" >&2; exit 1; }
done
command -v javac >/dev/null || { echo "缺少 javac" >&2; exit 1; }
command -v keytool >/dev/null || { echo "缺少 keytool" >&2; exit 1; }

if [ -d "$P/tests/com" ] && [ "${SKIP_TESTS:-0}" != "1" ]; then
  echo "== 0/7 单元测试（核心算法，纯 JVM）"
  bash "$P/tests/run_tests.sh"
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes"


echo "== 1/7 编译资源"
$Q "$BT/aapt2" compile --dir "$P/res" -o "$OUT/res.zip"

echo "== 2/7 链接资源与清单"
$Q "$BT/aapt2" link -o "$OUT/base.apk" \
  -I "$PLAT" \
  --manifest "$P/AndroidManifest.xml" \
  "$OUT/res.zip" \
  --java "$OUT/gen" \
  --min-sdk-version 29 --target-sdk-version 36 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

echo "== 3/7 javac"
find "$P/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 17 -target 17 -encoding UTF-8 \
  -classpath "$PLAT:$LIBS/*" -d "$OUT/classes" @"$OUT/sources.txt"

echo "== 4/7 d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 29 \
  --lib "$PLAT" --output "$OUT" @"$OUT/classes.txt" "$LIBS"/*.jar

echo "== 5/7 打包 classes.dex"
python3 - "$OUT" <<'PY'
import sys, zipfile, os
out = sys.argv[1]
src = os.path.join(out, "base.apk")
dst = os.path.join(out, "unsigned.apk")
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    zout.write(os.path.join(out, "classes.dex"), "classes.dex")
print("packed", os.path.getsize(dst))
PY

echo "== 6/7 zipalign"
$Q "$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== 7/7 签名"
if [ ! -f "$KS" ]; then
  echo "生成调试签名（正式发布请替换为自己的 keystore）"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=PowerMeter, OU=Dev, O=PowerMeter, C=CN" >/dev/null 2>&1
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias "$ALIAS" \
  --out "$APK" "$OUT/aligned.apk"
java -jar "$BT/lib/apksigner.jar" verify --print-certs "$APK" > "$OUT/signature.txt"

echo "== 完成：$APK"
ls -lh "$APK"
