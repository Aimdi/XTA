plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// -PmockX=<url> builds an APK that reads XTA's stand-in for X (data/MockX.kt); never set for a release.
val mockX = (findProperty("mockX") as String?).orEmpty()

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
        // Only the stand-in, on the emulator's host, is reached without TLS.
        manifestPlaceholders["cleartext"] = mockX.isNotEmpty().toString()
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
    implementation(project(":core"))

    val compose = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(compose)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
