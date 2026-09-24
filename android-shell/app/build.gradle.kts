// `java` is the project's own JavaPluginExtension inside a build script, so the digest
// has to be imported rather than spelled out as java.security.MessageDigest.
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI/sandbox builds may keep the default app/build intermediates locked. Allow
// a caller to move only generated artifacts while leaving source and APK
// configuration unchanged for normal Android Studio builds.
providers.gradleProperty("qplayerBuildDir").orNull?.let { requestedBuildDir ->
    layout.buildDirectory.set(file(requestedBuildDir))
}

android {
    namespace = "io.github.timer_err.qml4j.android"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.t1m3.qplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 67
        versionName = "1.3.0"
        manifestPlaceholders["appLabel"] = "QPlayer"
        // onnxruntime-android ships .so for four ABIs (~135 MB together) and this
        // is a phone-only player. Keep arm64-v8a and drop the 32-bit and emulator
        // slices; add "armeabi-v7a" back here if an old 32-bit phone ever matters.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Release signing is driven by env vars so the keystore never lives in the repo.
    // CI decodes a base64 keystore secret to a file and exports QPLAYER_KEYSTORE; a
    // local/secret-less build skips signing and produces an unsigned release apk.
    val keystorePath = System.getenv("QPLAYER_KEYSTORE")
    val hasSigning = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("QPLAYER_STORE_PASSWORD")
                keyAlias = System.getenv("QPLAYER_KEY_ALIAS")
                keyPassword = System.getenv("QPLAYER_KEY_PASSWORD")
            }
        }
        // The default Android debug keystore can be locked by Windows build
        // environments. Use an ignored local fallback when it exists.
        val localDebugKeystore = rootProject.file("local-debug.keystore")
        if (localDebugKeystore.exists()) {
            create("localDebug") {
                storeFile = localDebugKeystore
                storePassword = "qplayer-debug"
                keyAlias = "qplayer-debug"
                keyPassword = "qplayer-debug"
            }
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        // Distinct applicationId so a debug build installs alongside the
        // (differently-signed) release without a signature-mismatch conflict.
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "QPlayer (debug)"
            signingConfigs.findByName("localDebug")?.let { signingConfig = it }
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/*.kotlin_module"
        )
        // Keep native libraries uncompressed and 16 KB ZIP-aligned when a
        // dependency supplies them. The Compose entry point currently has no
        // native runtime dependency, so no .so is packaged below.
        jniLibs.useLegacyPackaging = false
    }
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

// ---------------------------------------------------------------------------
// The stem model (htdemucs-quarter.onnx, ~98 MB) is STAGED INTO THIS BUILD'S ASSETS
// FROM OUTSIDE THE REPOSITORY, and the build FAILS if it cannot be staged.
//
// Why it is not in git: 98 MB on a disk with ~11 GB free, in every clone, for a file
// a clone cannot even use (the weights live at D:\qplayer-dev\htdemucs\ — see
// AI_HANDOFF.md §零). Why it must be in the APK anyway: the app is handed to other
// people as an APK, and the feature has to work the moment they install it — no adb
// push, no download, no manual step. The app therefore carries the model as an asset
// and AndroidStemEditRenderer copies it into its private files/models/ on first run,
// where the same manifest check (name + bytes + sha256) that has always been the gate
// decides whether it may be used.
//
// Why the build must fail rather than warn: an APK built without the model installs
// and then does nothing, silently — the listener sees a feature that is off for no
// reason anyone can see. The failure names the path, the byte count and the sha256 it
// wanted, and the property that points the build at a copy somewhere else.
//
// The three numbers below duplicate StemModel.QUARTER (player-core,
// dev.t1m3.qplayer.audio): that manifest is compiled into the app and is what actually
// admits a file at runtime, this one decides what goes into the APK, and the two are
// compared on the device (the "does not match the manifest" refusal). StemModelTest
// pins the Java side, so a regenerated model has to move all of them together.
val stemModelName = "htdemucs-quarter.onnx"
val stemModelBytes = 97_978_156L
val stemModelSha256 = "427b9588287d85d78f212d9f6f4acbc42b626e28ef9d2d948fed78311f4aec20"
// Where the model is read from: -PqplayerStemModel=<path>, else $QPLAYER_STEM_MODEL,
// else the development copy on D:. Only this path is configurable — whether the file
// must be there at all is not.
val stemModelFile: Provider<File> = providers.gradleProperty("qplayerStemModel")
    .orElse(providers.environmentVariable("QPLAYER_STEM_MODEL"))
    .orElse("D:/qplayer-dev/htdemucs/$stemModelName")
    .map { File(it) }

// Where the staged copy goes is AGP's business: the variant wiring below hands this task the
// generated-assets directory it owns (`build/generated/assets/stageStemModel/`), which is
// also why nothing needs to be told where to find it — and why `build/` keeps the 98 MB out
// of git. The task cannot be run on its own for the same reason: `:app:assembleDebug` (or any
// variant's assets merge) is what stages it.

/**
 * Puts the stem model into the assets/ root this build's APK is packaged from — at
 * `models/<name>`, which is the path the app reads out of the APK — after checking that
 * what it is about to ship is the model the app's own manifest admits.
 *
 * It is a task class rather than a `Copy` because AGP has to know this directory is
 * produced by a task: {@code variant.sources.assets.addGeneratedSourceDirectory} below is
 * what both adds the assets/models/ root and makes every variant's packaging wait for it.
 * A `srcDir(...)` pointing at a directory some task writes is only an input — if the
 * wiring is not seen, the build succeeds and the APK simply has no model in it, which is
 * the one failure mode this whole step exists to prevent.
 *
 * Every failure here is a `GradleException` naming the path, the byte count and the
 * sha256, because the alternative is an APK that installs on someone else's phone and
 * quietly does nothing: the app refuses to feed audio through weights it cannot identify
 * (StemModel.recognise), and it cannot identify a model that was never put in the APK.
 */
abstract class StageStemModel : DefaultTask() {
    /** The model, read from outside the repository. Absent is a failure this task reports
     *  itself (with @Optional so Gradle does not fail it first with its own message). */
    @get:InputFile
    @get:Optional
    abstract val modelFile: RegularFileProperty

    /** The manifest's numbers, so a swapped or truncated model is caught in the build
     *  rather than by every device that installs the APK. */
    @get:Input
    abstract val expectedBytes: Property<Long>

    @get:Input
    abstract val expectedSha256: Property<String>

    /** The assets/ root this task produces, handed to it by the variant wiring below; the
     *  model lands at models/<name> inside it. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun stage() {
        val source = modelFile.asFile.get()
        val wanted = "${source.name}, ${expectedBytes.get()} bytes, " +
            "sha256 ${expectedSha256.get()}"
        if (!source.isFile) {
            throw GradleException(
                "The stem model is not where this build looks for it: ${source.absolutePath}\n" +
                    "  wanted: $wanted\n" +
                    "  Put the file there, or point the build at it with -PqplayerStemModel=<path>" +
                    " (or QPLAYER_STEM_MODEL=<path>).\n" +
                    "  It is deliberately NOT in the repository (98 MB, and other people clone" +
                    " this): the development copy lives in D:\\qplayer-dev\\htdemucs\\.\n" +
                    "  The build stops here rather than producing an APK without it — such an" +
                    " APK installs and then runs the stem path inert on every device, with" +
                    " nothing on screen to say why."
            )
        }
        if (source.length() != expectedBytes.get()) {
            throw GradleException(
                "The stem model at ${source.absolutePath} is ${source.length()} bytes, not" +
                    " ${expectedBytes.get()} — a truncated copy, or the other htdemucs model" +
                    " (the app accepts both halves, but this build ships one: $wanted).\n" +
                    "  Re-copy or regenerate it, then build again."
            )
        }

        val models = outputDir.get().asFile.resolve("models")
        if (!models.isDirectory && !models.mkdirs()) {
            throw GradleException("Cannot create the assets directory ${models.absolutePath}.")
        }
        // The staged directory survives between builds, so anything in it that is not this
        // build's model would be packaged alongside it. There is only ever one model in an APK.
        models.listFiles()?.forEach { if (it.name != source.name) it.delete() }

        val target = models.resolve(source.name)
        source.copyTo(target, overwrite = true)

        // Verified after the copy, on the bytes that will actually be packaged: a disk error
        // between the two reads is exactly the kind of corruption that shows up as "refusing
        // it, so the stem path stays inert" on a device, weeks later.
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().buffered(1 shl 16).use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        if (!sha256.equals(expectedSha256.get(), ignoreCase = true)) {
            throw GradleException(
                "The stem model staged into assets/ has sha256 $sha256, not" +
                    " ${expectedSha256.get()} — it is not the model the app's manifest admits," +
                    " so an APK built with it would refuse the file on every device.\n" +
                    "  If the model was deliberately regenerated, move all of them together:" +
                    " StemModel.QUARTER, StemModelTest, and the three values at the top of" +
                    " android-shell/app/build.gradle.kts."
            )
        }
        logger.lifecycle(
            "staged the stem model into the APK's assets: $source -> $target" +
                " (${target.length()} bytes, sha256 $sha256)"
        )
    }
}

val stageStemModel = tasks.register<StageStemModel>("stageStemModel") {
    group = "build"
    description = "Stages the stem model into the APK's assets/models/ (from outside the repo);" +
        " fails the build when it is absent or is not StemModel.QUARTER."
    modelFile.set(layout.file(stemModelFile))
    expectedBytes.set(stemModelBytes)
    expectedSha256.set(stemModelSha256)
}

// Every variant (debug and release alike) carries the model, and its packaging waits for the
// staging task. This is the AGP-supported way to feed assets from a task's output directory.
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageStemModel) { it.outputDir }
    }
}

android {
    // Vendored md3.Core component library + fonts (from the qml4j repo's shared-qml),
    // at the repo root so desktop-host can share it too. rootDir is android-shell/,
    // so the repo root is one level up. These QML/font assets aren't published in the
    // qml4j-core jar, so the app bundles them to build standalone.
    sourceSets["main"].assets.srcDir("${rootDir}/../shared-qml")

    androidResources {
        // The model is 98 MB of already-compressed float weights: deflating it costs build
        // time and buys a couple of percent, and the app copies it out of the APK anyway.
        // Stored, the first-run copy is a straight read (and the APK's size is predictable).
        noCompress += "onnx"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 Expressive APIs, including LoadingIndicator.
    implementation("androidx.compose.material3:material3:1.4.0-alpha18")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Battle-tested LazyColumn drag-to-reorder (same library PixelPlayer uses).
    // 2.2.0 targets Compose foundation 1.6.x — matches our BOM. Pure Compose,
    // no native code, so the 16 KB alignment requirement is unaffected.
    implementation("sh.calvin.reorderable:reorderable:2.2.0")

    // The RECODE refactor merged parser/engine/compiler/render into one module.
    implementation("io.github.timer-err:qml4j-core:0.2.29")

    // Our platform-neutral player core (netease + lyrics + audio abstraction +
    // QML bridge). Pulls gson + zxing-core transitively; all Android-dexable.
    // Player core: netease + lyrics + audio abstraction + QML bridge + the
    // host-drawn lyric page (fluid SkSL backdrop + per-syllable renderer + the
    // QML/Skija frame compositor), shared with the desktop host.
    // Local development fallback: the Android shell is intentionally kept
    // buildable when the snapshot has not been installed into D:/qplayer-dev.
    // CI/release builds can restore the Maven coordinate by removing this
    // fallback after publishing player-core.
    val localPlayerCore = rootProject.file("../player-core/target/player-core-0.1.0-SNAPSHOT.jar")
    if (localPlayerCore.exists()) {
        implementation(files(localPlayerCore))
    } else {
        implementation("dev.t1m3.qplayer:player-core:0.1.0-SNAPSHOT")
    }

    // player-core is commonly loaded from the local snapshot JAR above. A
    // flat JAR does not carry Maven transitive dependencies into the Android
    // runtime, so declare its pure-Java runtime dependencies explicitly.
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.tukaani:xz:1.12")
    implementation("com.google.zxing:core:3.5.3")

    implementation("io.github.humbleui:skija-shared:0.143.17")

    implementation("com.android.tools:r8:8.13.17")

    implementation("androidx.appcompat:appcompat:1.7.0")

    // MediaSessionCompat + MediaStyle notification + media-button handling for
    // system media controls (lockscreen / notification / bluetooth).
    implementation("androidx.media:media:1.7.0")

    // htdemucs stem separation for the AI DJ transition (see AI_HANDOFF.md §7).
    // This is the ONE dependency added on top of the "no new dependencies" rule,
    // and it is deliberate: the model's iSTFT sets DFT inverse+onesided together,
    // which ORT 1.22/1.23 refuse to load at all. Maven Central is reachable; the
    // Google Maven restriction that bans media3/Palette does not apply here.
    // Measured on 2026-09-19: the debug APK went 64,902,780 -> 94,053,487 bytes,
    // i.e. +29.1 MB, which is the arm64-v8a slice only (the aar itself is 53.0 MB
    // and carries four ABIs; see the abiFilters in defaultConfig). Verified to load
    // the sha256-checked model on a Redmi K20 Pro — see AI_HANDOFF.md §7.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.30.0")
}
