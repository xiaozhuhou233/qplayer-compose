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

android {
    // Vendored md3.Core component library + fonts (from the qml4j repo's shared-qml),
    // at the repo root so desktop-host can share it too. rootDir is android-shell/,
    // so the repo root is one level up. These QML/font assets aren't published in the
    // qml4j-core jar, so the app bundles them to build standalone.
    sourceSets["main"].assets.srcDir("${rootDir}/../shared-qml")
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
}
