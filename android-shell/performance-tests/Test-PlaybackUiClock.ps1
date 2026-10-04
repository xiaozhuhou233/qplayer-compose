$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cache = Join-Path $project 'gradle-cache/caches/modules-2/files-2.1'
function Cached([string]$module, [string]$pattern) {
    $found = Get-ChildItem -LiteralPath (Join-Path $cache $module) -Filter $pattern -Recurse -File | Select-Object -First 1
    if (!$found) { throw "Missing cached dependency: $module/$pattern" }
    return $found.FullName
}
$classes = Join-Path $project 'android-shell/app/build/performance-checks'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$appClasses = Join-Path $project 'android-shell/app/build/tmp/kotlin-classes/debug'
if (!(Test-Path (Join-Path $appClasses 'dev/t1m3/qplayer/android/ui/PlayerUiState.class'))) {
    throw 'Build the Android app first; this test checks its actual compiled PlayerUiState.'
}
$stdlib = Cached 'org.jetbrains.kotlin/kotlin-stdlib/2.0.21' '*.jar'
$annotations = Cached 'org.jetbrains/annotations/13.0' '*.jar'
$coroutines = Cached 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4' '*.jar'
$compiler = @(
    (Cached 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21' '*.jar'),
    $stdlib, $annotations, $coroutines,
    (Cached 'org.jetbrains.intellij.deps/trove4j/1.0.20200330' '*.jar'),
    (Cached 'org.jetbrains.kotlin/kotlin-script-runtime/2.0.21' '*.jar'),
    (Cached 'org.jetbrains.kotlin/kotlin-reflect/1.6.10' '*.jar')
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$aar = [IO.Compression.ZipFile]::OpenRead((Cached 'androidx.compose.runtime/runtime-android/1.8.2' '*.aar'))
$runtime = Join-Path $classes 'compose-runtime.jar'
try { [IO.Compression.ZipFileExtensions]::ExtractToFile($aar.GetEntry('classes.jar'), $runtime, $true) }
finally { $aar.Dispose() }
$classpath = @($stdlib, $annotations, $runtime, $coroutines,
    (Join-Path $env:LOCALAPPDATA 'Android/Sdk/platforms/android-35/android.jar'),
    (Cached 'androidx.collection/collection-jvm/1.5.0' '*.jar'),
    (Join-Path $project 'player-core/target/classes'), $appClasses) -join ';'
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else { (Get-Command java -ErrorAction Stop).Source }
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect `
    -classpath $classpath -d $classes `
    (Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/PlaybackUiClock.kt') `
    (Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/CoverDecodePolicy.kt') `
    (Join-Path $PSScriptRoot 'PlaybackUiClockCheck.kt')
if ($LASTEXITCODE -ne 0) { throw "Performance check compile failed: $LASTEXITCODE" }
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.PlaybackUiClockCheckKt
if ($LASTEXITCODE -ne 0) { throw "Performance checks failed: $LASTEXITCODE" }
