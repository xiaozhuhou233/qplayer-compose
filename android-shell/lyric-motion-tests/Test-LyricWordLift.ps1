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
$ui = Join-Path $project 'android-shell/app/src/main/java/dev/t1m3/qplayer/android/ui'
$classes = Join-Path $project 'android-shell/app/build/lyric-motion-checks'
$checks = Join-Path $PSScriptRoot 'LyricWordLiftCheck.kt'
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$stdlib;$annotations" -d $classes (Join-Path $ui 'LyricWordLift.kt') (Join-Path $ui 'LyricLineSweep.kt') $checks
if ($LASTEXITCODE -ne 0) { throw "Motion check compile failed: $LASTEXITCODE" }
& $java -cp "$classes;$stdlib" dev.t1m3.qplayer.android.ui.LyricWordLiftCheckKt
if ($LASTEXITCODE -ne 0) { throw "Motion checks failed: $LASTEXITCODE" }
