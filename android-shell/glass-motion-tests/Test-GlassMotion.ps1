param([switch]$ColorOnly, [switch]$OpticsOnly)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cacheRoots = @((Join-Path $project 'gradle-cache'))
if ($env:GRADLE_USER_HOME) { $cacheRoots += $env:GRADLE_USER_HOME }
if ($env:USERPROFILE) { $cacheRoots += Join-Path $env:USERPROFILE '.gradle' }
function Find-CachedJar([string]$module, [string]$name) {
    foreach ($root in $cacheRoots) {
        $directory = Join-Path $root "caches/modules-2/files-2.1/$module"
        if (Test-Path -LiteralPath $directory) {
            $found = Get-ChildItem -LiteralPath $directory -Filter $name -File -Recurse | Select-Object -First 1
            if ($found) { return $found.FullName }
        }
    }
    throw "Missing cached $name. Sync/build this project once first."
}
$stdlib = Find-CachedJar 'org.jetbrains.kotlin/kotlin-stdlib/2.0.21' 'kotlin-stdlib-2.0.21.jar'
$annotations = Find-CachedJar 'org.jetbrains/annotations/13.0' 'annotations-13.0.jar'
$compiler = @(
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21' 'kotlin-compiler-embeddable-2.0.21.jar'),
    $stdlib, $annotations,
    (Find-CachedJar 'org.jetbrains.intellij.deps/trove4j/1.0.20200330' 'trove4j-1.0.20200330.jar'),
    (Find-CachedJar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4' 'kotlinx-coroutines-core-jvm-1.6.4.jar'),
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-script-runtime/2.0.21' 'kotlin-script-runtime-2.0.21.jar'),
    (Find-CachedJar 'org.jetbrains.kotlin/kotlin-reflect/1.6.10' 'kotlin-reflect-1.6.10.jar')
)
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else { (Get-Command java -ErrorAction Stop).Source }
$classes = Join-Path $project 'android-shell/app/build/glass-motion-checks'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Extract-ComposeJar([string]$module, [string]$aarName, [string]$jarName) {
    $aar = Find-CachedJar $module $aarName
    $jar = Join-Path $classes $jarName
    $archive = [IO.Compression.ZipFile]::OpenRead($aar)
    try { [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry('classes.jar'), $jar, $true) }
    finally { $archive.Dispose() }
    return $jar
}
$animation = Extract-ComposeJar 'androidx.compose.animation/animation-core-android/1.8.2' 'animation-core-release.aar' 'animation-core.jar'
$util = Extract-ComposeJar 'androidx.compose.ui/ui-util-android/1.8.2' 'ui-util-release.aar' 'ui-util.jar'
$source = Join-Path $project 'android-shell/app/src/main/java/com/kyant/backdrop/catalog/utils/RetargetableFloatSpring.kt'
$checks = Join-Path $PSScriptRoot 'GlassMotionCheck.kt'
$colorSource = Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/AndroidGlassColorModel.kt'
$colorChecks = Join-Path $PSScriptRoot 'GlassColorCheck.kt'
$samplingSource = Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/GlassSamplingPolicy.kt'
$samplingChecks = Join-Path $PSScriptRoot 'GlassSamplingCheck.kt'
$refractionSource = Join-Path $project 'android-shell/app/src/main/java/com/kyant/backdrop/effects/GlassLensGeometry.kt'
$shaderSource = Join-Path $project 'android-shell/app/src/main/java/com/kyant/backdrop/internal/Shaders.kt'
$refractionChecks = Join-Path $PSScriptRoot 'GlassRefractionCheck.kt'
$apkRefractionChecks = Join-Path $PSScriptRoot 'ApkAdaptiveRefractionCheck.kt'
$biliParameters = Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/BiliPaiGlassParameters.kt'
$biliShader = Join-Path $project 'android-shell/app/src/main/java/com/kyant/backdrop/internal/BiliPaiBloomShader.kt'
$biliChecks = Join-Path $PSScriptRoot 'BiliPaiGlassCheck.kt'
$classpath = "$stdlib;$annotations;$animation;$util"
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath $classpath -d $classes $source $checks $colorSource $colorChecks $samplingSource $samplingChecks $refractionSource $shaderSource $refractionChecks $apkRefractionChecks $biliParameters $biliShader $biliChecks
if ($LASTEXITCODE -ne 0) { throw "Glass check compile failed: $LASTEXITCODE" }
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.BiliPaiGlassCheckKt
if ($LASTEXITCODE -ne 0) { throw "BiliPai parameter checks failed: $LASTEXITCODE" }
if (!$ColorOnly -and !$OpticsOnly) {
    & $java -cp "$classes;$classpath" com.kyant.backdrop.catalog.utils.GlassMotionCheckKt
    if ($LASTEXITCODE -ne 0) { throw "Glass checks failed: $LASTEXITCODE" }
}
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.GlassColorCheckKt
if ($LASTEXITCODE -ne 0) { throw "Glass colour checks failed: $LASTEXITCODE" }
if (!$ColorOnly) {
    & $java -cp "$classes;$classpath" com.kyant.backdrop.effects.ApkAdaptiveRefractionCheckKt
    if ($LASTEXITCODE -ne 0) { throw "APK adaptive refraction checks failed: $LASTEXITCODE" }
    & $java -cp "$classes;$classpath" com.kyant.backdrop.effects.GlassRefractionCheckKt
    if ($LASTEXITCODE -ne 0) { throw "Glass refraction checks failed: $LASTEXITCODE" }
}
if (!$ColorOnly -and !$OpticsOnly) {
    & $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.GlassSamplingCheckKt
    if ($LASTEXITCODE -ne 0) { throw "Glass sampling checks failed: $LASTEXITCODE" }
}
