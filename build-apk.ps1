$ErrorActionPreference = "Stop"

# P1-4: paths are parameterised with env defaults, not hardcoded to one
# machine. Override with PROJ/SDK/JDK env vars when building elsewhere.
# The repo checkout itself is the default project dir when run from it.
if (-not $env:PROJ) { $PROJ = $PSScriptRoot } else { $PROJ = $env:PROJ }
if (-not $env:SDK) { $SDK = "$env:USERPROFILE\workspace\android-sdk" } else { $SDK = $env:SDK }
if (-not $env:JDK) { $JDK = "$env:USERPROFILE\workspace\tools\jdk17\jdk-17.0.20.1+1" } else { $JDK = $env:JDK }
$BT = "$SDK\build-tools\34.0.0"
$PLATFORM = "$SDK\platforms\android-34\android.jar"
# P1-4: single source of truth for version (do not hardcode here).
$VERSION_CODE = "50"; $VERSION_NAME = "2.12"
$versionFile = Join-Path $PROJ "version.properties"
if (Test-Path $versionFile) {
  foreach ($line in Get-Content $versionFile) {
    if ($line -match '^versionCode=(.*)$') { $VERSION_CODE = $Matches[1].Trim() }
    if ($line -match '^versionName=(.*)$') { $VERSION_NAME = $Matches[1].Trim() }
  }
}
$JAVA_BIN = "$JDK\bin"

$env:JAVA_HOME = $JDK
$env:PATH = "$JAVA_BIN;$BT;$env:PATH"

$OUT = "$PROJ\build-out"
$GEN = "$OUT\gen"
$CLS = "$OUT\classes"
$DEX = "$OUT\dex"

if (Test-Path $OUT) { Remove-Item -Recurse -Force $OUT }
New-Item -ItemType Directory -Force -Path $GEN | Out-Null
New-Item -ItemType Directory -Force -Path $CLS | Out-Null
New-Item -ItemType Directory -Force -Path $DEX | Out-Null

Write-Host "== aapt2 compile =="
& "$BT\aapt2.exe" compile --dir "$PROJ\app\src\main\res" -o "$OUT\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "== aapt2 link =="
& "$BT\aapt2.exe" link -o "$OUT\app-unsigned.apk" `
  -I $PLATFORM `
  --manifest "$PROJ\app\src\main\AndroidManifest.xml" `
  --java $GEN `
  --min-sdk-version 28 `
  --target-sdk-version 34 `
  --version-code $VERSION_CODE `
  --version-name $VERSION_NAME `
  "$OUT\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "== javac =="
$javaFiles = (Get-ChildItem -Path "$PROJ\app\src\main\java", $GEN -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName)
$sourcesFile = "$OUT\sources.txt"
[System.IO.File]::WriteAllLines($sourcesFile, $javaFiles)

& "$JAVA_BIN\javac.exe" `
  -encoding UTF-8 -source 8 -target 8 -nowarn `
  -cp $PLATFORM `
  -d $CLS `
  "@$sourcesFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "== d8 =="
& "$JAVA_BIN\jar.exe" cf "$OUT\classes.jar" -C $CLS .
if ($LASTEXITCODE -ne 0) { throw "jar cf failed" }
& "$BT\d8.bat" --lib $PLATFORM --min-api 28 --output $DEX "$OUT\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "== add classes.dex =="
Copy-Item "$OUT\app-unsigned.apk" "$OUT\app-dex.apk"
Push-Location $DEX
try {
  & "$JAVA_BIN\jar.exe" uf "$OUT\app-dex.apk" classes.dex
  if ($LASTEXITCODE -ne 0) { throw "jar uf failed" }
} finally {
  Pop-Location
}

Write-Host "== zipalign =="
& "$BT\zipalign.exe" -f 4 "$OUT\app-dex.apk" "$OUT\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "== keystore =="
# P1-4: a release keystore is supplied ONLY via env vars
# ($env:LOCKLAUNCHER_KEYSTORE + $env:LOCKLAUNCHER_KEYSTORE_PASS).
# This script never generates or commits a release key. Without those
# vars it falls back to a local debug key and says so loudly.
if ($env:LOCKLAUNCHER_KEYSTORE -and (Test-Path $env:LOCKLAUNCHER_KEYSTORE)) {
  $KS = $env:LOCKLAUNCHER_KEYSTORE
  $KS_PASS = "pass:$env:LOCKLAUNCHER_KEYSTORE_PASS"
  $KEY_PASS = if ($env:LOCKLAUNCHER_KEY_PASS) { "pass:$env:LOCKLAUNCHER_KEY_PASS" } else { $KS_PASS }
  Write-Host "signing: RELEASE keystore from LOCKLAUNCHER_KEYSTORE (not committed)"
} else {
  Write-Host "WARNING: no LOCKLAUNCHER_KEYSTORE set - signing with DEBUG key only. This APK is NOT a release build."
  $KS = "$PROJ\debug.keystore"
  $KS_PASS = "pass:android"
  $KEY_PASS = "pass:android"
  if (-not (Test-Path $KS)) {
    & "$JAVA_BIN\keytool.exe" -genkeypair -v `
      -keystore $KS -storepass android -keypass android `
      -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 `
      -dname "CN=Android Debug,O=Android,C=US"
    if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
  }
}

Write-Host "== apksigner =="
& "$BT\apksigner.bat" sign --ks $KS --ks-pass $KS_PASS --key-pass $KEY_PASS `
  --out "$OUT\locklauncher.apk" "$OUT\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "== verify =="
& "$BT\apksigner.bat" verify --print-certs "$OUT\locklauncher.apk"
& "$BT\aapt2.exe" dump badging "$OUT\locklauncher.apk" | Select-Object -First 4
Get-Item "$OUT\locklauncher.apk" | Select-Object Name, Length, LastWriteTime
Write-Host "BUILD_OK"
