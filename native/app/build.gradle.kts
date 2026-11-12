// The Android app: a shell around :ui's screens (the Application, the activity
// and its links, signing, the baseline profile).
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// -PmockX=<url> builds an APK that reads XTA's stand-in for X (:ui's data/MockX.kt); never set for a release.
val mockX = (findProperty("mockX") as String?).orEmpty()
// -PpluginCatalogue=<url> reads the plugin catalogue there instead of the published one (CI's emulator check); never set for a release.
val pluginCatalogue = (findProperty("pluginCatalogue") as String?).orEmpty()
// -PvoiceServer=<url> downloads voices and the voice engine from there, and lets CI start the voice check (tool/voice_screens.sh); never set for a release.
val voiceServer = (findProperty("voiceServer") as String?).orEmpty()
// A release's or prerelease's APK downloads the voice engine from its own release first, then the
// latest, as XTA's does: NATIVE_RELEASE_TAG names it (v1.0.0), else a prerelease's native-<version>.
val releaseTag = System.getenv("NATIVE_RELEASE_TAG")?.takeIf { it.isNotBlank() }
    ?: System.getenv("NATIVE_VERSION_NAME")?.takeIf { it.isNotBlank() }?.let { "native-$it" }.orEmpty()

/** [file] read as Java properties, or null when there is none. */
fun propertiesOf(file: File): Properties? = file.takeIf { it.isFile }?.let { found -> Properties().apply { found.inputStream().use { input -> load(input) } } }

/**
 * The NDK of the Android SDK in [sdk] to build with, as its version: the one
 * [preferred] names (ANDROID_NDK_HOME, a runner's default) when the SDK holds
 * it, else the newest it holds; null when it holds none.
 */
fun chosenNdk(sdk: String?, preferred: String?): String? {
    fun part(version: String, index: Int): Int = version.split('.').getOrNull(index)?.toIntOrNull() ?: 0
    val installed = File(sdk ?: return null, "ndk").listFiles().orEmpty()
        .mapNotNull { ndk -> propertiesOf(File(ndk, "source.properties"))?.getProperty("Pkg.Revision")?.trim()?.takeIf { it == ndk.name } }
    return preferred?.let { File(it).name }?.takeIf { it in installed }
        ?: installed.maxWithOrNull(compareBy<String>({ part(it, 0) }, { part(it, 1) }, { part(it, 2) }))
}

val androidSdk: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    ?: propertiesOf(rootProject.file("local.properties"))?.getProperty("sdk.dir")

android {
    namespace = "com.aimdi.xtanative"
    compileSdk = 35

    defaultConfig {
        // Beside XTA, never over it: another id, so trying this changes nothing in XTA.
        applicationId = "com.aimdi.xtanative"
        // XTA runs from Android 7 (24); this stays at 8 (26): the code throughout, the plugins'
        // included, uses java.time, java.util.Base64 and java.nio.file, which Android has from 8,
        // and desugaring them for 7 would rename java.time in the app but not in the plugin APKs
        // that call it (plugins/android.gradle), so no plugin would link.
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("NATIVE_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("NATIVE_VERSION_NAME") ?: "0.1.0"
        buildConfigField("String", "MOCK_X", "\"$mockX\"")
        buildConfigField("String", "PLUGIN_CATALOGUE", "\"$pluginCatalogue\"")
        buildConfigField("String", "VOICE_SERVER", "\"$voiceServer\"")
        buildConfigField("String", "RELEASE_TAG", "\"$releaseTag\"")
        // Only the stand-ins, on the emulator's host, are reached without TLS.
        manifestPlaceholders["cleartext"] = (mockX.isNotEmpty() || pluginCatalogue.startsWith("http://") || voiceServer.startsWith("http://")).toString()
    }

    // The JNI shim downloaded voices speak through (services/src/main/c), a
    // few kilobytes for each processor, built with the NDK's own ndk-build
    // where one is installed, as on CI's runners. Without one the APK leaves
    // it out: voices still download, and the system's engine reads.
    chosenNdk(androidSdk, System.getenv("ANDROID_NDK_HOME") ?: System.getenv("ANDROID_NDK_ROOT"))?.let { ndk ->
        ndkVersion = ndk
        externalNativeBuild {
            ndkBuild {
                path = file("../services/src/main/c/Android.mk")
            }
        }
    }

    // Lifecycle 2.9's lint check for LiveData crashes this Android plugin's lint
    // (IncompatibleClassChangeError); the app has no LiveData to check.
    lint {
        disable += "NullSafeMutableLiveData"
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("KEY_PATH")
            if (!path.isNullOrEmpty()) {
                storeFile = file(path)
                storePassword = System.getenv("KEY_STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (System.getenv("KEY_PATH").isNullOrEmpty()) signingConfigs.getByName("debug")
                else signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            // The voice shim is the app's only native code, a few kilobytes a
            // processor: one APK for every phone.
            isEnable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ui"))
    // Its plugin verifier and class loader (plugins/AndroidPlugins.kt).
    implementation(project(":plugin-host"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
}

// Settings › App info › Licences: what each variant's APK ships, with the licences (gradle/licences.gradle.kts).
apply(from = rootProject.file("gradle/licences.gradle.kts"))
@Suppress("UNCHECKED_CAST")
val writeLicences = extra["writeLicences"] as (String, File) -> Unit

abstract class VariantLicences : DefaultTask() {
    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Internal
    var write: (File) -> Unit = {}

    @TaskAction
    fun run() {
        val folder = output.get().asFile
        folder.deleteRecursively()
        write(folder)
    }
}

androidComponents {
    onVariants { variant ->
        val licences = tasks.register<VariantLicences>("${variant.name}Licences") {
            // The libraries are Gradle's to resolve each build; listing them is quick.
            outputs.upToDateWhen { false }
            write = { folder -> writeLicences("${variant.name}RuntimeClasspath", folder) }
        }
        variant.sources.resources?.addGeneratedSourceDirectory(licences, VariantLicences::output)
    }
}
