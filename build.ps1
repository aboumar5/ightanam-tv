# Builds ightanam-tv.apk without Gradle, using the Android SDK command-line tools.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$sdk   = "$env:LOCALAPPDATA\Android\Sdk"
$bt    = "$sdk\build-tools\35.0.0"
$jar   = "$sdk\platforms\android-35\android.jar"
$jdk   = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "$env:USERPROFILE\dev\jdk17" }
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"

Remove-Item build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build\classes, build\dex | Out-Null

# --- icon + banner (gold on a night sky, matching the clock page) ---
& (Join-Path $PSScriptRoot 'art.ps1')

# --- Thmanyah font (optional) ---
# Its license forbids redistributing the files, so they aren't in the repo. Download the font from
# https://font.thmanyah.com and drop the .ttf/.otf files into .\fonts; without them a system font is used.
# -NoFontAssets builds without bundling them (for a public download: an APK's assets can be extracted).
$assets = @()
$bundleFont = -not ($args -contains '-NoFontAssets')
# Kept at the assets root: aapt2 on Windows stores subfolder paths with backslashes Android can't open.
New-Item -ItemType Directory -Force build\assets | Out-Null
foreach ($w in 'Regular', 'Bold', 'Black') {
    $f = Get-ChildItem fonts -Recurse -Include "*$w.ttf", "*$w.otf" -ErrorAction SilentlyContinue |
         Where-Object Name -match '^thmanyah' | Where-Object Name -notmatch 'serif' | Select-Object -First 1
    if ($f -and $bundleFont) { Copy-Item $f.FullName ("build\assets\font-" + $w.ToLower() + $f.Extension.ToLower()); $assets = '-A', 'build\assets' }
}

# --- resources + manifest ---
& "$bt\aapt2.exe" compile --dir res -o build\res.zip
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }
& "$bt\aapt2.exe" link -o build\base.apk -I $jar --manifest AndroidManifest.xml `
    --min-sdk-version 26 --target-sdk-version 30 @assets build\res.zip
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

# --- java -> dex ---
$srcs = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object FullName
& javac --release 11 -encoding UTF-8 -cp $jar -d build\classes @srcs
if ($LASTEXITCODE) { throw 'javac failed' }
$classes = Get-ChildItem build\classes -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --release --min-api 26 --lib $jar --output build\dex @classes
if ($LASTEXITCODE) { throw 'd8 failed' }

Push-Location build\dex
& "$bt\aapt.exe" add ..\base.apk classes.dex | Out-Null
Pop-Location

# --- align + sign (debug key) ---
$ks = "$env:USERPROFILE\.android\debug.keystore"
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
    & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey `
        -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
}
& "$bt\zipalign.exe" -f 4 build\base.apk build\aligned.apk
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out ightanam-tv.apk build\aligned.apk
if ($LASTEXITCODE) { throw 'apksigner failed' }
Write-Host "Built $PSScriptRoot\ightanam-tv.apk"
