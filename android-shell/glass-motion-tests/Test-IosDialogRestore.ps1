param(
    [string]$ReferenceBackup = 'backups/glass-before-bilipai-20261002-163859/current-glass-source.zip'
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$javaRoot = Join-Path $project 'android-shell/app/src/main/java'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $project $ReferenceBackup))
function Normalize([string]$text) { $text.Replace("`r`n", "`n").Trim() }
function Read-Backup([string]$path) {
    $entry = $archive.GetEntry('java/' + $path)
    if ($null -eq $entry) { throw "Missing backup source: $path" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { Normalize $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Assert-Equal([string]$name, [string]$expected, [string]$actual) {
    if ($expected -cne $actual) { throw "FAIL: $name differs from the backup" }
    Write-Output "PASS: $name"
}
try {
    $ui = 'dev/t1m3/qplayer/android/ui/'
    $dialog = Normalize ([IO.File]::ReadAllText((Join-Path $javaRoot ($ui + 'IosDialogs.kt'))))
    $normalizedDialog = $dialog.Replace('rememberRestoredIosDialogGlass(backdrop)', 'rememberIosAdaptiveGlass(backdrop)').
        Replace('restoredDialogGlassEffects(adaptive.luminance, refraction)', 'apiGlassEffects(adaptive.luminance, refraction)').
        Replace('RestoredIosDialogHighlight', 'IosGlassDialogHighlight').
        Replace('RestoredIosDialogShadow', 'IosGlassShadow').
        Replace("                LocalRestoredIosDialogGlass provides true,`n", '').
        Replace("    val tilt = if (enabled) rememberBiliPaiDeviceTilt() else null`n", '').
        Replace('CompositionLocalProvider(LocalDialogBackdrop provides state, LocalBiliPaiDeviceTilt provides tilt)',
                'CompositionLocalProvider(LocalDialogBackdrop provides state)')
    Assert-Equal 'dialog matches backup apart from scoped dependency names and shared BiliPai host' `
        (Read-Backup ($ui + 'IosDialogs.kt')) $normalizedDialog

    $originalSampler = Read-Backup ($ui + 'IosAdaptiveGlass.kt')
    $restoredSampler = Normalize ([IO.File]::ReadAllText((Join-Path $javaRoot ($ui + 'RestoredIosDialogGlass.kt'))))
    $start = 'private val glassReadbackLock'
    $oldStart = $originalSampler.IndexOf($start)
    $oldEnd = $originalSampler.IndexOf("`n@Composable`ninternal fun adaptiveGlassInk()")
    $newStart = $restoredSampler.IndexOf($start)
    $newEnd = $restoredSampler.IndexOf("`n/**`n * Dialog-only restoration")
    if ($oldStart -lt 0 -or $oldEnd -lt 0 -or $newStart -lt 0 -or $newEnd -lt 0) {
        throw 'Sampler source boundaries not found'
    }
    $samplerCore = $restoredSampler.Substring($newStart, $newEnd - $newStart).
        Replace('private class RestoredDialogGlassSampler', 'internal class IosAdaptiveGlassState').
        Replace('private fun rememberRestoredDialogGlassSampler', 'internal fun rememberIosAdaptiveGlass').
        Replace('RestoredDialogGlassSampler', 'IosAdaptiveGlassState').
        Replace('restoredDialogContentColor', 'glassContentColor')
    Assert-Equal 'dialog sampling and animation logic matches backup' `
        (Normalize $originalSampler.Substring($oldStart, $oldEnd - $oldStart)) (Normalize $samplerCore)

    foreach ($path in @(($ui + 'AndroidGlassColorModel.kt'), ($ui + 'GlassSamplingPolicy.kt'), 'com/kyant/backdrop/effects/Lens.kt')) {
        Assert-Equal $path (Read-Backup $path) (Normalize ([IO.File]::ReadAllText((Join-Path $javaRoot $path))))
    }
    if (!$restoredSampler.Contains('staticCompositionLocalOf { false }') -or
        !$restoredSampler.Contains('else apiGlassEffects(adaptive.luminance, refraction)') -or
        !$restoredSampler.Contains('else iosGlassShadow(dark)') -or
        !$restoredSampler.Contains('if (!adaptive.restoredDialog) drawBiliPaiGlassSurface(dark)')) {
        throw 'Missing dialog isolation / BiliPai fallback'
    }
    Write-Output 'PASS: restoration defaults off; non-dialog controls retain BiliPai effects'
} finally { $archive.Dispose() }
