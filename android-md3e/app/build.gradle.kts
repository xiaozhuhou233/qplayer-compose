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
