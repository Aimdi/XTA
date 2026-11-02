import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The screens, view models, theme and navigation the Android app (:app) and the
// Linux app (:desktop) share, with the parts of the app graph no platform owns:
// Compose Multiplatform. What differs per platform (players, the backup file
// chooser, date patterns) is in androidMain and desktopMain.
//
// The only targets are JVM ones, so commonMain is compiled as JVM code: it uses
// the JDK and :core directly. The Android half joins only where an Android SDK
// is installed, like :app, so the desktop builds with a JDK alone.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val androidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
if (androidSdk) apply(plugin = "com.android.library")

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    if (androidSdk) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    sourceSets {
        // What a downloadable plugin builds its screens with is api (PLUGINS.md).
        commonMain.dependencies {
            api(project(":core"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            api(libs.compose.material.icons.core)
            api(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            implementation(libs.compose.components.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
        }
        val desktopMain by getting {
            dependencies {
                // Dispatchers.Main, which view models run on, is the Swing thread here.
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        if (androidSdk) {
            androidMain.dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.activity.compose)
                implementation(libs.media3.exoplayer)
                implementation(libs.media3.ui)
            }
        }
    }
}

if (androidSdk) apply(from = "android.gradle")

compose.resources {
    packageOfResClass = "com.aimdi.xtanative.ui.generated.resources"
}
