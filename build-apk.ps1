$ErrorActionPreference = "Stop"

$PROJ = "c:\Users\velaz\Documents\antigravity\beautiful-pasteur"
$SDK = "C:\Users\velaz\workspace\android-sdk"
$BT = "$SDK\build-tools\34.0.0"
$PLATFORM = "$SDK\platforms\android-34\android.jar"
$JDK = "C:\Users\velaz\workspace\tools\jdk17\jdk-17.0.20.1+1"
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
  --version-code 41 `
  --version-name "1.75" `
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
$classFiles = (Get-ChildItem -Path $CLS -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName)
& "$BT\d8.bat" --lib $PLATFORM --min-api 28 --output $DEX $classFiles
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
$KS = "$PROJ\debug.keystore"
if (-not (Test-Path $KS)) {
  & "$JAVA_BIN\keytool.exe" -genkeypair -v `
    -keystore $KS -storepass android -keypass android `
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 `
    -dname "CN=Android Debug,O=Android,C=US"
  if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

Write-Host "== apksigner =="
& "$BT\apksigner.bat" sign --ks $KS --ks-pass pass:android --key-pass pass:android `
  --out "$PROJ\locklauncher.apk" "$OUT\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "== verify =="
& "$BT\apksigner.bat" verify --print-certs "$PROJ\locklauncher.apk"
& "$BT\aapt2.exe" dump badging "$PROJ\locklauncher.apk" | Select-Object -First 4
Get-Item "$PROJ\locklauncher.apk" | Select-Object Name, Length, LastWriteTime
Write-Host "BUILD_OK"
