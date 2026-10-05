$ErrorActionPreference = 'Stop'
$targetRoot = 'C:\Users\xiaoz\.codex\worktrees\888a\qplayer'
$targetUi = Join-Path $targetRoot 'android-md3e/app/src/main/java/dev/t1m3/qplayer/android/md3eui'
$targetHome = Join-Path $targetUi 'Md3eHome.kt'
$expected = Get-Content -LiteralPath $targetHome -Raw
if ($expected -notmatch 'SpinningDeformIcon\(') { throw 'AI DJ source changed; inspect before applying.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'SongChecklistWriting.kt') -Destination (Join-Path $targetUi 'SongChecklistWriting.kt')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Md3eHome.kt') -Destination $targetHome
$env:QPLAYER_MD3E_ROOT = $targetRoot
& 'C:\Users\xiaoz\Desktop\Build-QPlayer-MD3E.ps1' -BuildOnly
