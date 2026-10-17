$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$cache = 'D:/qplayer-dev/cache/gradle/caches/modules-2/files-2.1'
function Jar([string]$module) {
    (Get-ChildItem -LiteralPath (Join-Path $cache $module) -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
}
$stdlib = Jar 'org.jetbrains.kotlin/kotlin-stdlib/2.0.21'
$annotations = Jar 'org.jetbrains/annotations/13.0'
$compiler = @(
    (Jar 'org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21'), $stdlib, $annotations,
    (Jar 'org.jetbrains.intellij.deps/trove4j/1.0.20200330'),
    (Jar 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4'),
    (Jar 'org.jetbrains.kotlin/kotlin-script-runtime/2.0.21'),
    (Jar 'org.jetbrains.kotlin/kotlin-reflect/1.6.10')
)
$java = 'C:/Program Files/Java/jdk-17/bin/java.exe'
$classes = Join-Path $PSScriptRoot 'checks'
New-Item -ItemType Directory -Force $classes | Out-Null
$source = Join-Path $repo 'android-md3e/app/src/main/java/dev/t1m3/qplayer/android/md3eui/Md3eMvClip.kt'
& $java -cp ($compiler -join ';') org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath "$stdlib;$annotations" -d $classes $source (Join-Path $PSScriptRoot 'MvWideCheck.kt')
if ($LASTEXITCODE -ne 0) { throw 'MV check compilation failed' }
& $java -cp "$classes;$stdlib" dev.t1m3.qplayer.android.md3eui.MvWideCheckKt 'C:/Users/xiaoz/Downloads/Screenshot_20261017_164519_bilibili.jpg'
if ($LASTEXITCODE -ne 0) { throw 'MV checks failed' }
