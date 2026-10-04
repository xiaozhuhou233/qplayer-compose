param(
    [string]$Apk = (Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk'),
    [string]$Sdk = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$dexdump = Join-Path $Sdk 'build-tools/35.0.0/dexdump.exe'
if (-not (Test-Path -LiteralPath $dexdump)) { throw "Missing SDK dexdump: $dexdump" }
$auditRoot = Join-Path $PSScriptRoot 'app/build/apk-audit'
New-Item -ItemType Directory -Force $auditRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($apkPath)
try {
    $definitions = @()
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName -match '(?i)(\.onnx$|onnxruntime)') { throw "Unexpected AI asset: $($entry.FullName)" }
        if ($entry.FullName -notmatch '^classes[0-9]*\.dex$') { continue }
        $dexPath = Join-Path $auditRoot $entry.FullName
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dexPath, $true)
        $definitions += @(& $dexdump $dexPath | Select-String '^\s*Class descriptor\s*:' | ForEach-Object { $_.Line })
        if ($LASTEXITCODE -ne 0) { throw "dexdump failed for $($entry.FullName)" }
    }
    foreach ($required in @(
        'dev/t1m3/qplayer/android/md3eui/Md3eActivity',
        'dev/t1m3/qplayer/android/md3eui/Md3eBiliKt',
        'dev/t1m3/qplayer/android/md3eui/Md3eNeteaseLoginKt',
        'dev/t1m3/qplayer/android/playback/AndroidAudioBackend',
        'dev/t1m3/qplayer/android/playback/PlaybackService',
        'dev/t1m3/qplayer/android/graphics/AndroidColorExtractor',
        'dev/t1m3/qplayer/bridge/PlayerController',
        'dev/t1m3/qplayer/netease/NeteaseClient',
        'dev/t1m3/qplayer/bili/BiliClient',
        'dev/t1m3/qplayer/unblock/SongUnblocker',
        'dev/t1m3/qplayer/unblock/NeteaseSource',
        'dev/t1m3/qplayer/unblock/BodianSource',
        'dev/t1m3/qplayer/unblock/KuwoSource'
    )) {
        if (-not ($definitions | Select-String -SimpleMatch "L$required;")) { throw "Missing required class: $required" }
    }
    $forbidden = @($definitions | Where-Object {
        $_ -match 'Lcom/kyant/backdrop/' -or
        $_ -match 'Ldev/t1m3/qplayer/android/ui/' -or
        $_ -match 'Ldev/t1m3/qplayer/android/stem/'
    })
    if ($forbidden.Count) { throw "Unexpected legacy UI/AI classes: $($forbidden -join ', ')" }
    "APK isolation passed: $($definitions.Count) class definitions; MD3E root and existing playback present; old UI, glass and ONNX absent."
} finally {
    $zip.Dispose()
}
