import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// XTA Native on the Linux desktop: :ui's screens in one window, over the same
// :core client the Android app reads X with. createDistributable makes the app
// with a Java runtime of its own (build/compose/binaries/main/app/xta-native);
// tool/package_desktop.sh packs that as the tarball and the Arch package.
plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
}

val versionName = System.getenv("NATIVE_VERSION_NAME") ?: "0.1.0"

compose.desktop {
    application {
        mainClass = "com.aimdi.xtanative.desktop.MainKt"
        // Settings shows it; the Android app reads its own from the package.
        jvmArgs("-Dxtanative.version=$versionName")
        nativeDistributions {
            packageName = "xta-native"
            packageVersion = versionName
            // Beside the modules Compose always ships: German month names and
            // separators (jdk.localedata), and what the libraries may load.
            modules("jdk.localedata", "jdk.unsupported", "java.naming", "java.management", "java.instrument", "java.sql")
        }
    }
}
