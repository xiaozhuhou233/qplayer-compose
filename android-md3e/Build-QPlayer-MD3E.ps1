param(
    [string]$Serial,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'

$candidates = @(
    $env:QPLAYER_MD3E_ROOT,
    'C:\Users\xiaoz\.codex\worktrees\888a\qplayer',
    $env:QPLAYER_HOME,
    'C:\Users\xiaoz\Desktop\SkidTime\qplayer'
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
$projectRoot = $candidates | Where-Object {
    Test-Path -LiteralPath (Join-Path $_ 'android-md3e/settings.gradle.kts')
} | Select-Object -First 1
if (-not $projectRoot) {
    throw 'MD3E project not found. Set QPLAYER_MD3E_ROOT to the repository root.'
}
$projectRoot = (Resolve-Path -LiteralPath $projectRoot).Path
$md3eRoot = Join-Path $projectRoot 'android-md3e'
$apk = Join-Path $md3eRoot 'app/build/outputs/apk/debug/app-debug.apk'

$cachedGradle = 'D:\qplayer-dev\cache\gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat'
$gradle = if (Test-Path -LiteralPath $cachedGradle) { $cachedGradle }
    else { Join-Path $projectRoot 'android-shell/gradlew.bat' }
if (-not (Test-Path -LiteralPath $gradle)) { throw "Gradle 8.7 not found: $gradle" }

$gradleArgs = @('-p', $md3eRoot, ':app:assembleDebug', '--no-daemon')
$mavenCache = 'D:\qplayer-dev\cache\maven'
if (Test-Path -LiteralPath $mavenCache) {
    $gradleArgs += "-Dmaven.repo.local=$mavenCache"
    $gradleArgs += '--offline'
}
Write-Host "Building QPlayer MD3E: $md3eRoot" -ForegroundColor Cyan
Push-Location $projectRoot
try {
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: $LASTEXITCODE" }
} finally { Pop-Location }

if (-not (Test-Path -LiteralPath $apk)) { throw "APK not found: $apk" }
Write-Host 'Checking APK contents...' -ForegroundColor Cyan
& (Join-Path $md3eRoot 'verify-apk.ps1') -Apk $apk
if ($LASTEXITCODE -ne 0) { throw "APK verification failed: $LASTEXITCODE" }
$apkFile = Get-Item -LiteralPath $apk
$apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
Write-Host "APK: $apk ($($apkFile.Length) bytes)" -ForegroundColor Green
Write-Host "SHA-256: $apkHash" -ForegroundColor Green
if ($BuildOnly) { return }

$adbCandidates = @(
    if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' }
    'C:\Users\xiaoz\AppData\Local\Android\Sdk\platform-tools\adb.exe'
    'D:\qplayer-dev\download\platform-tools\adb.exe'
)
$adb = $adbCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $adb) {
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) { $adb = $command.Source }
}
if (-not $adb) { throw 'adb not found. Install Android platform-tools, then rerun this script.' }

$deviceLines = @(& $adb devices -l)
if ($LASTEXITCODE -ne 0) { throw "adb devices failed: $LASTEXITCODE" }
$connected = @($deviceLines | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' } |
    ForEach-Object { ($_ -split '\s+')[0] })
$physical = @($connected | Where-Object {
    if ($_ -match '^emulator-\d+$') { return $false }
    $qemu = (& $adb -s $_ shell getprop ro.kernel.qemu 2>$null | Out-String).Trim()
    $bootQemu = (& $adb -s $_ shell getprop ro.boot.qemu 2>$null | Out-String).Trim()
    $LASTEXITCODE -eq 0 -and $qemu -ne '1' -and $bootQemu -ne '1'
})

if ($Serial) {
    if ($Serial -notin $physical) { throw "Specified physical device is not ready: $Serial" }
    $target = $Serial
} elseif ($physical.Count -eq 1) {
    $target = $physical[0]
} elseif ($physical.Count -eq 0) {
    Write-Warning 'No authorized physical Android device found. APK is built; connect your phone and rerun.'
    return
} else {
    throw "Multiple physical devices found ($($physical -join ', ')). Rerun with -Serial <device serial>."
}

Write-Host "Installing on physical device: $target" -ForegroundColor Cyan
& $adb -s $target install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed: $LASTEXITCODE" }
Write-Host 'QPlayer MD3E installed.' -ForegroundColor Green
