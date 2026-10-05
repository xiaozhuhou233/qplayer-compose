$ErrorActionPreference = 'Stop'
$targetRoot = 'C:\Users\xiaoz\.codex\worktrees\888a\qplayer'
$targetMain = Join-Path $targetRoot 'android-md3e/app/src/main'
$targetRes = Join-Path $targetMain 'res'
New-Item -ItemType Directory -Force (Join-Path $targetRes 'mipmap-anydpi-v26') | Out-Null
foreach ($name in @('ic_md3e.xml', 'ic_launcher_foreground.xml')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $targetRes "drawable/$name")
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'ic_launcher.xml') -Destination (Join-Path $targetRes 'mipmap-anydpi-v26/ic_launcher.xml')
foreach ($name in @('launcher_colors.xml', 'launcher_alias.xml')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $targetRes "values/$name")
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'AndroidManifest.xml') -Destination (Join-Path $targetMain 'AndroidManifest.xml')
$env:QPLAYER_MD3E_ROOT = $targetRoot
& 'C:\Users\xiaoz\Desktop\Build-QPlayer-MD3E.ps1' -BuildOnly
