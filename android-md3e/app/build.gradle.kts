plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Since Kotlin 1.8 these compatibility artifacts are merged into kotlin-stdlib.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}
val releaseStore = providers.environmentVariable("QPLAYER_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("QPLAYER_RELEASE_STORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("QPLAYER_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("QPLAYER_RELEASE_KEY_PASSWORD").orNull
val releaseSigningRequested = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
    .any { it != null }
if (releaseSigningRequested) {
    require(listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() }) { "Set all four QPLAYER_RELEASE signing environment variables." }
}
android {
    namespace = "dev.t1m3.qplayer.android.md3eui"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.t1m3.qplayer.md3e"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-md3e"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { compose = true }
    signingConfigs {
        if (releaseSigningRequested) create("production") {
            storeFile = file(requireNotNull(releaseStore))
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
        val existing = rootProject.file("../android-shell/local-debug.keystore")
        if (existing.exists()) create("workspaceDebug") {
            storeFile = existing
            storePassword = "qplayer-debug"
            keyAlias = "qplayer-debug"
            keyPassword = "qplayer-debug"
        }
    }
    buildTypes.named("debug") {
        signingConfigs.findByName("workspaceDebug")?.let { signingConfig = it }
    }
    buildTypes.named("release") {
        isDebuggable = false
        signingConfig = signingConfigs.findByName("production")
    }
    packaging.resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/*.kotlin_module")
}
dependencies {
    implementation(project(":platform"))
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.4.0-alpha18")
    implementation("androidx.media:media:1.7.0")
    implementation("sh.calvin.reorderable:reorderable:2.2.0")
}
