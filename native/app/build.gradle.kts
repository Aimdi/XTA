// The Android app: a shell around :ui's screens (the Application, the activity
// and its links, signing, the baseline profile).
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// -PmockX=<url> builds an APK that reads XTA's stand-in for X (:ui's data/MockX.kt); never set for a release.
val mockX = (findProperty("mockX") as String?).orEmpty()
// -PpluginCatalogue=<url> reads the plugin catalogue there instead of the published one (CI's emulator check); never set for a release.
val pluginCatalogue = (findProperty("pluginCatalogue") as String?).orEmpty()

android {
    namespace = "com.aimdi.xtanative"
    compileSdk = 35

    defaultConfig {
        // Beside XTA, never over it: another id, so trying this changes nothing in XTA.
        applicationId = "com.aimdi.xtanative"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("NATIVE_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("NATIVE_VERSION_NAME") ?: "0.1.0"
        buildConfigField("String", "MOCK_X", "\"$mockX\"")
        buildConfigField("String", "PLUGIN_CATALOGUE", "\"$pluginCatalogue\"")
        // Only the stand-ins, on the emulator's host, are reached without TLS.
        manifestPlaceholders["cleartext"] = (mockX.isNotEmpty() || pluginCatalogue.startsWith("http://")).toString()
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
            // Kotlin has no native code of its own; the split only matters for
            // libraries that bring some.
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
