#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$KeyStore = (Join-Path $env:USERPROFILE '.android/sign/TEST'),
    [string]$KeyAlias = 'key0',
    [Security.SecureString]$StorePassword,
    [Security.SecureString]$KeyPassword,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Sdk = $env:ANDROID_HOME,
    [string]$Gradle,
    [string]$MavenRepo,
    [string]$OutputDirectory,
    [switch]$Offline,
    [switch]$NonInteractive,
    [switch]$SkipInstall,
    [string]$DeviceSerial
)
$ErrorActionPreference = 'Stop'
$project = $PSScriptRoot
$repo = Split-Path $project -Parent
if (-not (Test-Path -LiteralPath "$project/app/build.gradle.kts")) {
    throw 'Place this script in the android-md3e directory.'
}
if (-not $JavaHome) { $JavaHome = 'C:/Program Files/Java/jdk-17' }
if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (-not $Sdk) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$java = Join-Path $JavaHome 'bin/java.exe'
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
$buildTools = Join-Path $Sdk 'build-tools/35.0.0'
foreach ($required in @($java, $keytool, $KeyStore, "$buildTools/apksigner.bat",
        "$buildTools/aapt.exe", "$buildTools/zipalign.exe", "$buildTools/dexdump.exe",
        "$project/verify-apk.ps1")) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing file: $required" }
}
$KeyStore = (Resolve-Path -LiteralPath $KeyStore).Path
if (-not $Gradle) {
    $cachedGradle = 'D:/qplayer-dev/cache/gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle.bat'
    $Gradle = if (Test-Path -LiteralPath $cachedGradle) { $cachedGradle } else { "$repo/android-shell/gradlew.bat" }
}
if (-not (Test-Path -LiteralPath $Gradle -PathType Leaf)) { throw "Missing Gradle launcher: $Gradle" }
$Gradle = (Resolve-Path -LiteralPath $Gradle).Path
if (-not $MavenRepo -and (Test-Path -LiteralPath 'D:/qplayer-dev/cache/maven')) {
    $MavenRepo = 'D:/qplayer-dev/cache/maven'
}
if ($MavenRepo) { $MavenRepo = (Resolve-Path -LiteralPath $MavenRepo).Path }
if (-not $OutputDirectory) { $OutputDirectory = "$project/app/build/release-delivery" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$run = Join-Path $OutputDirectory ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6))
New-Item -ItemType Directory -Path $run | Out-Null

# Restore the caller's process environment even when signing or compilation fails.
$names = @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_USER_HOME', 'GRADLE_USER_HOME',
    'QPLAYER_KEYSTORE', 'QPLAYER_STORE_PASSWORD',
    'QPLAYER_KEY_ALIAS', 'QPLAYER_KEY_PASSWORD')
$saved = @{}
foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $Sdk
    $env:ANDROID_USER_HOME = "$repo/.android-user-home"
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = if (Test-Path -LiteralPath 'D:/qplayer-dev/cache/gradle/caches') {
            'D:/qplayer-dev/cache/gradle'
        } else { "$repo/gradle-cache" }
    }
    $env:QPLAYER_KEYSTORE = $KeyStore
    $env:QPLAYER_KEY_ALIAS = $KeyAlias
    if (-not $StorePassword -and -not $env:QPLAYER_STORE_PASSWORD) {
        if ($NonInteractive) { throw 'Set QPLAYER_STORE_PASSWORD or pass -StorePassword.' }
        $StorePassword = Read-Host 'Keystore password (hidden)' -AsSecureString
    }
    if ($StorePassword) {
        $env:QPLAYER_STORE_PASSWORD = [Net.NetworkCredential]::new('', $StorePassword).Password
    }
    if ($KeyPassword) {
        $env:QPLAYER_KEY_PASSWORD = [Net.NetworkCredential]::new('', $KeyPassword).Password
    } elseif (-not $env:QPLAYER_KEY_PASSWORD) {
        # TEST uses the same store and key password. Other stores can override it.
        $env:QPLAYER_KEY_PASSWORD = $env:QPLAYER_STORE_PASSWORD
    }
    if (-not $env:QPLAYER_STORE_PASSWORD -or -not $env:QPLAYER_KEY_PASSWORD) {
        throw 'Signing passwords must not be empty.'
    }
    Write-Host 'Checking signing certificate...'
    $certificate = Join-Path $run 'signer.der'
    & $keytool -exportcert -keystore $KeyStore -alias $KeyAlias -storepass:env QPLAYER_STORE_PASSWORD -file $certificate *> "$run/keystore.log"
    if ($LASTEXITCODE -ne 0) { throw "Keystore check failed. See $run/keystore.log" }
    $expectedSigner = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
    $gradleArgs = @('-p', $project, ':app:assembleRelease', '--no-daemon', '--no-configuration-cache', '--console=plain')
    if ($MavenRepo) { $gradleArgs += "-Dmaven.repo.local=$MavenRepo" }
    if ($Offline) { $gradleArgs += '--offline' }
    Write-Host "Building Release. Log: $run/build.log"
    & $Gradle @gradleArgs *> "$run/build.log"
    if ($LASTEXITCODE -ne 0) { throw "Release build failed. See $run/build.log" }
    $apk = "$project/app/build/outputs/apk/release/app-release.apk"
    if (-not (Test-Path -LiteralPath $apk)) { throw 'Signed Release APK was not produced.' }
    & "$buildTools/apksigner.bat" verify --verbose --print-certs $apk *> "$run/signature.log"
    if ($LASTEXITCODE -ne 0) { throw "Signature verification failed. See $run/signature.log" }
    $signature = Get-Content -LiteralPath "$run/signature.log" -Raw
    $signers = [regex]::Matches($signature, '(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\s*$')
    if ($signers.Count -ne 1 -or $signers[0].Groups[1].Value.ToLowerInvariant() -ne $expectedSigner) {
        throw 'APK signer does not match the selected keystore alias.'
    }
    & "$buildTools/zipalign.exe" -c -P 16 4 $apk *> "$run/alignment.log"
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment check failed.' }
    & "$buildTools/aapt.exe" dump badging $apk *> "$run/apk-info.log"
    if ($LASTEXITCODE -ne 0) { throw 'APK manifest check failed.' }
    $info = Get-Content -LiteralPath "$run/apk-info.log" -Raw
    if ($info -match '(?m)^application-debuggable') { throw 'Release APK must not be debuggable.' }
    if ($info -notmatch "(?m)^package: name='dev.t1m3.qplayer.md3e'") { throw 'Unexpected application ID.' }
    & "$project/verify-apk.ps1" -Apk $apk -Sdk $Sdk *> "$run/isolation.log"
    $version = [regex]::Match($info, "versionName='([^']+)'").Groups[1].Value
    $version = $version -replace '[^a-zA-Z0-9._-]', '_'
    $delivery = Join-Path $run "QPlayer-$version-release.apk"
    Copy-Item -LiteralPath $apk -Destination $delivery
    $hash = (Get-FileHash -LiteralPath $delivery -Algorithm SHA256).Hash
    "$hash  $([IO.Path]::GetFileName($delivery))" | Set-Content -LiteralPath "$delivery.sha256" -Encoding ASCII
    Write-Host "Release ready: $delivery"
    Write-Host "Signer SHA256: $expectedSigner"
    Write-Host "APK SHA256: $hash"
    if (-not $SkipInstall) {
        $adb = Join-Path $Sdk 'platform-tools/adb.exe'
        if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
            Write-Warning 'APK built. Android platform-tools/adb.exe missing: installation skipped.'
        } else {
            . (Join-Path $PSScriptRoot 'Install-ReleaseApk.ps1')
            Install-ReleaseApk -AdbPath $adb -Apk $delivery -LogDirectory $run -DeviceSerial $DeviceSerial -NonInteractive:$NonInteractive
        }
    }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    $StorePassword = $null
    $KeyPassword = $null
}
