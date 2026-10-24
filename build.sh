#!/usr/bin/env bash
# Build tvstat without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Requires JDK 17 and an Android SDK with build-tools 34.0.0 + platform android-31.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$ROOT/sdk}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-31/android.jar"
export JAVA_HOME="${JAVA_HOME:-$ROOT/jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

PKG_PATH="dev/godlesscherry/tvstat"
OUT="$ROOT/build"
APP="$ROOT/app"
KS="$ROOT/debug.keystore"

for f in "$BT/aapt2" "$BT/d8" "$BT/apksigner" "$BT/zipalign" "$ANDROID_JAR"; do
  [ -e "$f" ] || { echo "missing $f" >&2; echo "set ANDROID_SDK_ROOT to an SDK with build-tools;34.0.0 and platforms;android-31" >&2; exit 1; }
done
command -v javac >/dev/null || { echo "javac not found — set JAVA_HOME to a JDK 17" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "1/5 aapt2 compile"
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "2/5 aapt2 link"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" --java "$OUT/gen" "$OUT/res.zip"

echo "3/5 javac"
javac -nowarn -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$OUT/classes" \
  "$APP/java/$PKG_PATH"/*.java "$OUT/gen/$PKG_PATH/R.java"
[ -f "$OUT/classes/$PKG_PATH/MainActivity.class" ] || { echo "FATAL: MainActivity did not compile" >&2; exit 1; }

echo "4/5 d8"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$BT/d8" --lib "$ANDROID_JAR" --min-api 21 --output "$OUT/dex" "$OUT/classes.jar"

echo "5/5 package + sign"
cp "$OUT/base.apk" "$OUT/unaligned.apk"
APK="$OUT/unaligned.apk"
(cd "$OUT/dex" && zip -q "$APK" classes.dex)
"$BT/zipalign" -f 4 "$OUT/unaligned.apk" "$OUT/aligned.apk"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/build/tvstat.apk" "$OUT/aligned.apk"

echo
ls -lh "$ROOT/build/tvstat.apk"
