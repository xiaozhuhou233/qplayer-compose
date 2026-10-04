$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cache = Join-Path $project 'gradle-cache/caches/modules-2/files-2.1'
function Cached([string]$module, [string]$pattern) {
    $found = Get-ChildItem -LiteralPath (Join-Path $cache $module) -Filter $pattern -Recurse -File | Select-Object -First 1
    if (!$found) { throw "Missing cached dependency: $module/$pattern" }
    return $found.FullName
}
$classes = Join-Path $project 'android-shell/app/build/lyric-line-lift-checks'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
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
function Extract-Classes([string]$module, [string]$name) {
    $aar = [IO.Compression.ZipFile]::OpenRead((Cached $module '*.aar'))
    $jar = Join-Path $classes $name
    try { [IO.Compression.ZipFileExtensions]::ExtractToFile($aar.GetEntry('classes.jar'), $jar, $true) }
    finally { $aar.Dispose() }
    return $jar
}
$runtime = Extract-Classes 'androidx.compose.runtime/runtime-android/1.8.2' 'compose-runtime.jar'
$animation = Extract-Classes 'androidx.compose.animation/animation-core-android/1.8.2' 'compose-animation.jar'
# The animation runtime only needs this coroutine-context interface from UI.
# Copy the actual cached classes into a tiny JAR; no mocked animation classes.
$composeUi = Join-Path $classes 'compose-ui-motion-scale.jar'
$uiAar = [IO.Compression.ZipFile]::OpenRead((Cached 'androidx.compose.ui/ui-android/1.8.2' '*.aar'))
$uiMemory = [IO.MemoryStream]::new()
try {
    $source = $uiAar.GetEntry('classes.jar').Open()
    try { $source.CopyTo($uiMemory) } finally { $source.Dispose() }
    $uiMemory.Position = 0
    $inputJar = [IO.Compression.ZipArchive]::new($uiMemory, [IO.Compression.ZipArchiveMode]::Read, $true)
    $outputJar = [IO.Compression.ZipArchive]::new([IO.File]::Create($composeUi), [IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        foreach ($entry in $inputJar.Entries) {
            if ($entry.FullName -notlike 'androidx/compose/ui/MotionDurationScale*.class') { continue }
            $source = $entry.Open()
            $destination = $outputJar.CreateEntry($entry.FullName).Open()
            try { $source.CopyTo($destination) } finally { $source.Dispose(); $destination.Dispose() }
        }
    } finally { $inputJar.Dispose(); $outputJar.Dispose() }
} finally { $uiMemory.Dispose(); $uiAar.Dispose() }
$classpath = @($stdlib, $annotations, $runtime, $animation, $composeUi, $coroutines,
    (Join-Path $env:LOCALAPPDATA 'Android/Sdk/platforms/android-35/android.jar'),
    (Cached 'androidx.collection/collection-jvm/1.5.0' '*.jar')) -join ';'
$java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    Join-Path $env:JAVA_HOME 'bin/java.exe'
} else { (Get-Command java -ErrorAction Stop).Source }
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect `
    -classpath $classpath -d $classes `
    (Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui/LyricLineLift.kt') `
    (Join-Path $PSScriptRoot 'LyricLineLiftCheck.kt')
if ($LASTEXITCODE -ne 0) { throw "Line lift check compile failed: $LASTEXITCODE" }
& $java -cp "$classes;$classpath" dev.t1m3.qplayer.android.ui.LyricLineLiftCheckKt
if ($LASTEXITCODE -ne 0) { throw "Line lift checks failed: $LASTEXITCODE" }
