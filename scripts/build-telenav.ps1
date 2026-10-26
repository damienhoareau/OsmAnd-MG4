<#
.SYNOPSIS
  Builds the MG4 "Telenav identity" OsmAnd APK on this PC, signs it with the AOSP platform key
  (the key every MG4 system app is signed with) and can install it on the head unit.

.DESCRIPTION
  Expected layout: the OsmAnd resources repository sits next to this repository (<parent>\resources);
  it is cloned (shallow) when missing. The legacy C++ core is not built (ANDROID_NDK is blanked): the
  OpenGL core comes precompiled from builder.osmand.net.

.EXAMPLE
  .\scripts\build-telenav.ps1
  .\scripts\build-telenav.ps1 -Install            # also push and install on the connected head unit
  .\scripts\build-telenav.ps1 -BuildType Release  # needs the release signing setup of OsmAnd, not for the car
#>
param(
	[ValidateSet('Arm64', 'Armv7', 'Armonly', 'Fat')]
	[string]$Abi = 'Arm64',
	[ValidateSet('Debug', 'Release')]
	[string]$BuildType = 'Debug',
	[string]$Jdk = 'C:\Users\damie\jdks\jdk-21.0.12.1+1',
	[string]$SdkDir = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
	[string]$BuildTools = '36.1.0',
	[string]$Keystore = 'C:\Users\damie\jdks\aosp_keys\aosp-platform.p12',
	[string]$KeyAlias = 'platform',
	[string]$StorePassword = 'android',
	[switch]$Install,
	[string]$Serial
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$resources = Join-Path (Split-Path -Parent $repo) 'resources'
$apksigner = Join-Path $SdkDir "build-tools\$BuildTools\apksigner.bat"
$adb = Join-Path $SdkDir 'platform-tools\adb.exe'

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

# Windows PowerShell 5.1 turns anything a native command writes on stderr (Gradle and the SDK tools print
# harmless warnings there) into an error: run them with errors non-terminating and check the exit code.
function Invoke-Native([scriptblock]$command) {
	$previous = $ErrorActionPreference
	$ErrorActionPreference = 'Continue'
	try { & $command } finally { $ErrorActionPreference = $previous }
}

Step 'Checking prerequisites'
foreach ($p in @($Jdk, $SdkDir, $apksigner, $Keystore)) {
	if (-not (Test-Path $p)) { throw "Missing: $p" }
}
if (-not (Test-Path $resources)) {
	Step "Cloning OsmAnd-resources into $resources"
	Invoke-Native { git clone --depth 1 https://github.com/osmandapp/OsmAnd-resources $resources }
	if ($LASTEXITCODE -ne 0) { throw 'git clone failed' }
}

$localProperties = Join-Path $repo 'local.properties'
if (-not (Test-Path $localProperties)) {
	"sdk.dir=" + ($SdkDir -replace '\\', '\\\\') | Set-Content -Encoding ASCII $localProperties
}

Step "Gradle: assembleTelenavOpengl$Abi$BuildType"
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
$env:ANDROID_NDK = ''
$env:ANDROID_NDK_ROOT = ''
$env:APP_EDITION = 'mg4-telenav'
Push-Location $repo
try {
	Invoke-Native { .\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8' "assembleTelenavOpengl$Abi$BuildType" }
	if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }
} finally {
	Pop-Location
}

$apk = Get-ChildItem (Join-Path $repo "OsmAnd\build\outputs\apk\telenavOpengl$Abi\$($BuildType.ToLower())") -Filter *.apk |
	Select-Object -First 1
if (-not $apk) { throw 'APK not found in OsmAnd\build\outputs\apk' }

Step 'Signing with the platform key'
$outDir = Join-Path $repo 'OsmAnd\build\outputs\telenav'
New-Item -ItemType Directory -Force $outDir | Out-Null
$signed = Join-Path $outDir ("Nav-$Abi-$($BuildType.ToLower()).apk")
Invoke-Native { & $apksigner sign --ks $Keystore --ks-key-alias $KeyAlias --ks-pass "pass:$StorePassword" --key-pass "pass:$StorePassword" --out $signed $apk.FullName }
if ($LASTEXITCODE -ne 0) { throw 'apksigner sign failed' }
Invoke-Native { & $apksigner verify --print-certs $signed } | Select-String 'SHA-256'
Write-Host "Signed APK: $signed" -ForegroundColor Green

if ($Install) {
	Step 'Installing on the head unit'
	$adbArgs = @()
	if ($Serial) { $adbArgs += @('-s', $Serial) }
	# plain "adb install" is refused on this unit: push, then pm install -r -f (see MG4 notes)
	Invoke-Native { & $adb @adbArgs push $signed /data/local/tmp/nav-telenav.apk }
	Invoke-Native { & $adb @adbArgs shell 'pm install -r -f /data/local/tmp/nav-telenav.apk' }
	Invoke-Native { & $adb @adbArgs shell 'rm /data/local/tmp/nav-telenav.apk' }
}
