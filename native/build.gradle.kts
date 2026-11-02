// Every plugin is on the root classpath, so the Kotlin and Android plugins
// share one class loader. The Android plugin is added only where an SDK is
// installed: :core must build without one.
buildscript {
    repositories {
        maven("https://repo1.maven.org/maven2")
        if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) google()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.21")
        if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) {
            classpath("com.android.tools.build:gradle:8.7.3")
        }
    }
}
