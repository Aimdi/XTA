// Every plugin is on the root classpath, so the Kotlin, Compose and Android
// plugins share one class loader. The Android plugin is added only where an
// SDK is installed: :core, the desktop half of :ui and :desktop build without
// one. Versions are in gradle/libs.versions.toml.
buildscript {
    val androidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
    repositories {
        maven("https://repo1.maven.org/maven2")
        if (androidSdk) google()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.compose.compiler.gradle.plugin)
        classpath(libs.compose.gradle.plugin)
        if (androidSdk) classpath(libs.android.gradle.plugin)
    }
}
