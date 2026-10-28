#!/bin/bash
# Manual APK build for LockLauncher (no Gradle): aapt2 + javac + d8 + zipalign + apksigner
set -e
PROJ=$HOME/workspace/locklauncher-android
SDK=$HOME/workspace/android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
OUT=$PROJ/build-out
GEN=$OUT/gen
CLS=$OUT/classes
DEX=$OUT/dex

export PATH=$BT:$HOME/workspace/jdk17/jdk-17.0.11+9/bin:$PATH
export JAVA_HOME=$HOME/workspace/jdk17/jdk-17.0.11+9
rm -rf "$OUT"
mkdir -p "$OUT" "$GEN" "$CLS" "$DEX"

echo "== aapt2 compile =="
$BT/aapt2 compile --dir $PROJ/app/src/main/res -o $OUT/res.zip

echo "== aapt2 link =="
$BT/aapt2 link -o $OUT/app-unsigned.apk \
  -I $PLATFORM \
  --manifest $PROJ/app/src/main/AndroidManifest.xml \
  --java $GEN \
  --min-sdk-version 28 \
  --target-sdk-version 34 \
  --version-code 12 \
  --version-name "1.11" \
  $OUT/res.zip

echo "== javac =="
find $PROJ/app/src/main/java $GEN -name "*.java" > $OUT/sources.txt
$HOME/workspace/jdk17/jdk-17.0.11+9/bin/javac \
  -encoding UTF-8 -source 8 -target 8 -nowarn \
  -cp $PLATFORM \
  -d $CLS \
  @$OUT/sources.txt 2>&1 | grep -v "bootstrap\|deprecat" | head -20 || true

echo "== d8 =="
$BT/d8 --lib $PLATFORM --min-api 28 --output $DEX \
  $(find $CLS -name "*.class" | tr '\n' ' ')

echo "== add classes.dex =="
cp $OUT/app-unsigned.apk $OUT/app-dex.apk
cd $DEX && $HOME/workspace/jdk17/jdk-17.0.11+9/bin/jar uf $OUT/app-dex.apk classes.dex && cd -

echo "== zipalign =="
$BT/zipalign -f 4 $OUT/app-dex.apk $OUT/app-aligned.apk

echo "== keystore =="
KS=$PROJ/debug.keystore
if [ ! -f "$KS" ]; then
  $HOME/workspace/jdk17/jdk-17.0.11+9/bin/keytool -genkeypair -v \
    -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US" 2>&1 | tail -1
fi

echo "== apksigner =="
$BT/apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out $PROJ/locklauncher.apk $OUT/app-aligned.apk

echo "== verify =="
$BT/apksigner verify --print-certs $PROJ/locklauncher.apk | head -3
$BT/aapt2 dump badging $PROJ/locklauncher.apk | head -4
ls -la $PROJ/locklauncher.apk
echo BUILD_OK
