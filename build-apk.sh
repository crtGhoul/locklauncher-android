#!/bin/bash
# Manual APK build for LockLauncher (no Gradle): aapt2 + javac + d8 + zipalign + apksigner
set -e
# P1-4: paths are parameterised with env defaults, not hardcoded to one
# machine. Override with PROJ/SDK/JDK_DIR env vars when building elsewhere.
# The repo checkout itself is the default project dir when run from it.
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJ=${PROJ:-$SCRIPT_DIR}
SDK=${SDK:-$HOME/android-sdk}

if [ -f "$SDK/android-36/android.jar" ]; then
  DEFAULT_PLATFORM="$SDK/android-36/android.jar"
elif [ -f "$SDK/platforms/android-34/android.jar" ]; then
  DEFAULT_PLATFORM="$SDK/platforms/android-34/android.jar"
elif [ -f "$SDK/platforms/android-36/android.jar" ]; then
  DEFAULT_PLATFORM="$SDK/platforms/android-36/android.jar"
else
  DEFAULT_PLATFORM=$(find "$SDK" -name "android.jar" 2>/dev/null | head -1)
fi

PLATFORM=${PLATFORM:-$DEFAULT_PLATFORM}

# In CI / desktop with Android SDK build-tools, resolve binaries from build-tools if available and executable
BT=$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 2>/dev/null | sort -V | tail -1)
if [ -n "$BT" ] && [ -x "$BT/aapt2" ]; then
  AAPT2=${AAPT2:-$BT/aapt2}
  D8=${D8:-$BT/d8}
  ZIPALIGN=${ZIPALIGN:-$BT/zipalign}
  APKSIGNER=${APKSIGNER:-$BT/apksigner}
fi

AAPT2=${AAPT2:-$(command -v aapt2)}
JAVAC=${JAVAC:-$(command -v javac)}
D8=${D8:-$(command -v d8)}
ZIPALIGN=${ZIPALIGN:-$(command -v zipalign)}
APKSIGNER=${APKSIGNER:-$(command -v apksigner)}
JAR=${JAR:-$(command -v jar)}
KEYTOOL=${KEYTOOL:-$(command -v keytool)}
# P1-4: single source of truth for version (do not hardcode here).
if [ -f "$PROJ/version.properties" ]; then
  VERSION_CODE=$(grep '^versionCode=' "$PROJ/version.properties" | cut -d= -f2)
  VERSION_NAME=$(grep '^versionName=' "$PROJ/version.properties" | cut -d= -f2)
fi
VERSION_CODE=${VERSION_CODE:-58}
VERSION_NAME=${VERSION_NAME:-2.20}
OUT=$PROJ/build-out
GEN=$OUT/gen
CLS=$OUT/classes
DEX=$OUT/dex

rm -rf "$OUT"
mkdir -p "$OUT" "$GEN" "$CLS" "$DEX"

echo "== aapt2 compile =="
$AAPT2 compile --dir $PROJ/app/src/main/res -o $OUT/res.zip

echo "== aapt2 link =="
$AAPT2 link -o $OUT/app-unsigned.apk \
  -I $PLATFORM \
  --manifest $PROJ/app/src/main/AndroidManifest.xml \
  --java $GEN \
  --min-sdk-version 28 \
  --target-sdk-version 34 \
  --version-code $VERSION_CODE \
  --version-name "$VERSION_NAME" \
  $OUT/res.zip

echo "== javac =="
find $PROJ/app/src/main/java $GEN -name "*.java" > $OUT/sources.txt
$JAVAC \
  -encoding UTF-8 -source 8 -target 8 -nowarn \
  -cp $PLATFORM \
  -d $CLS \
  @$OUT/sources.txt
# No output masking here: a swallowed javac error once produced a signed
# APK with no classes.dex that still printed BUILD_OK. Fail hard instead.

echo "== d8 =="
$D8 --lib $PLATFORM --min-api 28 --output $DEX \
  $(find $CLS -name "*.class" | tr '\n' ' ')

echo "== add classes.dex =="
cp $OUT/app-unsigned.apk $OUT/app-dex.apk
cd $DEX && $JAR uf $OUT/app-dex.apk classes.dex && cd -

# Fail hard if javac/d8 produced no classes.dex (a dex-less APK still
# signs and badges cleanly, so this check is the only guard).
if [ ! -f "$DEX/classes.dex" ]; then
  echo "FATAL: classes.dex missing after javac/d8 - build aborted" >&2
  exit 1
fi

echo "== zipalign =="
$ZIPALIGN -f 4 $OUT/app-dex.apk $OUT/app-aligned.apk

echo "== keystore =="
# P1-4: a release keystore is supplied ONLY via env vars
# (LOCKLAUNCHER_KEYSTORE + LOCKLAUNCHER_KEYSTORE_PASS, and optionally
# LOCKLAUNCHER_KEY_ALIAS / LOCKLAUNCHER_KEY_PASS). This script never
# generates or commits a release key. Without those vars it falls back
# to a local debug key and says so loudly: a debug-signed APK must
# never be mistaken for a release build.
if [ -n "$LOCKLAUNCHER_KEYSTORE" ] && [ -f "$LOCKLAUNCHER_KEYSTORE" ]; then
  KS="$LOCKLAUNCHER_KEYSTORE"
  KS_PASS="pass:${LOCKLAUNCHER_KEYSTORE_PASS:-}"
  KEY_PASS="pass:${LOCKLAUNCHER_KEY_PASS:-${LOCKLAUNCHER_KEYSTORE_PASS:-}}"
  echo "signing: RELEASE keystore from LOCKLAUNCHER_KEYSTORE (not committed)"
else
  echo "WARNING: no LOCKLAUNCHER_KEYSTORE set - signing with DEBUG key only. This APK is NOT a release build."
  KS=$PROJ/debug.keystore
  KS_PASS="pass:android"
  KEY_PASS="pass:android"
  if [ ! -f "$KS" ]; then
    $KEYTOOL -genkeypair -v \
      -keystore "$KS" -storepass android -keypass android \
      -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
      -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -1
  fi
fi

echo "== apksigner =="
$APKSIGNER sign --ks "$KS" --ks-pass "$KS_PASS" --key-pass "$KEY_PASS" \
  --out $PROJ/build-out/locklauncher.apk $OUT/app-aligned.apk

echo "== verify =="
$APKSIGNER verify --print-certs $PROJ/build-out/locklauncher.apk | head -3
$AAPT2 dump badging $PROJ/build-out/locklauncher.apk | head -4
ls -la $PROJ/build-out/locklauncher.apk
echo BUILD_OK
