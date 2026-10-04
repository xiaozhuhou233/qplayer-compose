$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cache = Join-Path $project 'gradle-cache/caches/modules-2/files-2.1'
function Cached([string]$module, [string]$pattern) {
    $found = Get-ChildItem -LiteralPath (Join-Path $cache $module) -Filter $pattern -Recurse -File | Select-Object -First 1
    if (!$found) { throw "Missing cached dependency: $module/$pattern" }
    return $found.FullName
}
$classes = Join-Path $project 'android-shell/app/build/shared-motion-checks'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$appClasses = Join-Path $project 'android-shell/app/build/tmp/kotlin-classes/debug'
if (!(Test-Path (Join-Path $appClasses 'dev/t1m3/qplayer/android/ui/BiliPaiGlassParameters.class'))) {
    throw 'Build the Android app once first; this test uses its backdrop effect types.'
}
$stdlib = Cached 'org.jetbrains.kotlin/kotlin-stdlib/2.0.21' '*.jar'
$annotations = Cached 'org.jetbrains/annotations/13.0' '*.jar'
$coroutines = Cached 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.3' '*.jar'
$compiler = @(
    (Cached 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21' '*.jar'),
    $stdlib, $annotations, $coroutines,
    (Cached 'org.jetbrains.intellij.deps/trove4j/1.0.20200330' '*.jar'),
    (Cached 'org.jetbrains.kotlin/kotlin-script-runtime/2.0.21' '*.jar'),
    (Cached 'org.jetbrains.kotlin/kotlin-reflect/1.6.10' '*.jar')
)
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Extract-Compose([string]$module, [string]$name, [string]$version = '1.8.2') {
    $aar = [IO.Compression.ZipFile]::OpenRead((Cached "$module/$version" '*.aar'))
    $jar = Join-Path $classes "$name.jar"
    try { [IO.Compression.ZipFileExtensions]::ExtractToFile($aar.GetEntry('classes.jar'), $jar, $true) }
    finally { $aar.Dispose() }
    return $jar
}
$classpath = @($stdlib, $annotations, $coroutines,
    (Extract-Compose 'androidx.compose.runtime/runtime-android' 'compose-runtime'),
    (Extract-Compose 'androidx.compose.animation/animation-core-android' 'animation-core'),
    (Extract-Compose 'androidx.compose.animation/animation-android' 'animation'),
    (Extract-Compose 'androidx.compose.material3/material3-android' 'material3' '1.4.0-alpha18'),
    (Extract-Compose 'androidx.compose.ui/ui-android' 'compose-ui'),
    (Extract-Compose 'androidx.compose.ui/ui-graphics-android' 'compose-graphics'),
    (Extract-Compose 'androidx.compose.ui/ui-geometry-android' 'compose-geometry'),
    (Extract-Compose 'androidx.compose.ui/ui-unit-android' 'compose-unit'),
    (Extract-Compose 'androidx.compose.ui/ui-util-android' 'compose-util'),
    (Join-Path $env:LOCALAPPDATA 'Android/Sdk/platforms/android-35/android.jar'),
    (Cached 'androidx.collection/collection-jvm/1.5.0' '*.jar'), $appClasses) -join ';'
$composeCompiler = Cached 'org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.0.21' '*.jar'
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else { (Get-Command java -ErrorAction Stop).Source }
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect `
    "-Xplugin=$composeCompiler" "-Xfriend-paths=$appClasses" -classpath $classpath -d $classes `
    (Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/IosGlassPalette.kt') `
    (Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/BiliPaiGlassParameters.kt') `
    (Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/QPlayerThemeMotion.kt') `
    (Join-Path $PSScriptRoot 'AndroidTraceStub.kt') `
    (Join-Path $PSScriptRoot 'ThemeMotionCheck.kt') `
    (Join-Path $PSScriptRoot 'SharedMotionCheck.kt')
if ($LASTEXITCODE -ne 0) { throw "Shared motion check compile failed: $LASTEXITCODE" }
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.SharedMotionCheckKt
if ($LASTEXITCODE -ne 0) { throw "Shared motion checks failed: $LASTEXITCODE" }
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.SharedMotionCheckKt --static-control
if ($LASTEXITCODE -ne 0) { throw "Static local control failed: $LASTEXITCODE" }
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.ThemeMotionCheckKt
if ($LASTEXITCODE -ne 0) { throw "Theme motion scheduling checks failed: $LASTEXITCODE" }
