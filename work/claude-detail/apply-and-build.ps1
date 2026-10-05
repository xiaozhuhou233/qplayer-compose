$ErrorActionPreference = 'Stop'
$targetRoot = 'C:\Users\xiaoz\.codex\worktrees\888a\qplayer'
$targetUi = Join-Path $targetRoot 'android-md3e/app/src/main/java/dev/t1m3/qplayer/android/md3eui'
foreach ($name in @('Md3eHome.kt', 'Md3eLyrics.kt', 'Md3ePlayer.kt')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $targetUi $name)
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'ic_claude_ai_dj.xml') -Destination (Join-Path $targetRoot 'android-md3e/app/src/main/res/drawable/ic_claude_ai_dj.xml')
$env:QPLAYER_MD3E_ROOT = $targetRoot
& 'C:\Users\xiaoz\Desktop\Build-QPlayer-MD3E.ps1' -BuildOnly
