// Every plugin is on the root classpath, so the Kotlin, Compose and Android
// plugins share one class loader. The Android plugin is added only where an
// SDK is installed: :core, the desktop half of :ui and :desktop build without
// one. Versions are in gradle/libs.versions.toml.
buildscript {
    val androidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
    repositories {
        // Maven Central, as settings.gradle.kts explains.
        maven("https://maven-central.storage-download.googleapis.com/maven2")
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

// The key the app and its plugins are signed with (PLUGINS.md): the release key
// CI makes from its SIGNING_KEY secret (KEY_PATH, KEY_STORE_PASSWORD, KEY_ALIAS
// and KEY_PASSWORD, as the build job sets them), else this machine's Android
// debug key, the one an APK is signed with when there is no release key: a dev
// build. The desktop app is built with its certificate's SHA-256 and the
// plugins' JARs are signed with it; the Android builds use the same variables.
val releaseKey = System.getenv("KEY_PATH").orEmpty()
val signing: Map<String, String> = if (releaseKey.isNotEmpty()) {
    mapOf(
        "kind" to "release key",
        "path" to releaseKey,
        "storePassword" to System.getenv("KEY_STORE_PASSWORD").orEmpty(),
        "alias" to System.getenv("KEY_ALIAS").orEmpty(),
        "keyPassword" to System.getenv("KEY_PASSWORD").orEmpty(),
    )
} else {
    mapOf(
        "kind" to "this machine's Android debug key, a dev build",
        "path" to File(System.getProperty("user.home"), ".android/debug.keystore").path,
        "storePassword" to "android",
        "alias" to "androiddebugkey",
        "keyPassword" to "android",
    )
}
extra["signing"] = signing

// Makes the debug key where there is none yet, as the Android plugin makes it.
tasks.register("signingKey") {
    doLast {
        val store = File(signing.getValue("path"))
        if (releaseKey.isNotEmpty() || store.isFile) return@doLast
        store.parentFile.mkdirs()
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val made = ProcessBuilder(
            keytool, "-genkeypair", "-keystore", store.path, "-storepass", "android", "-alias", "androiddebugkey",
            "-keypass", "android", "-dname", "CN=Android Debug,O=Android,C=US", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10950",
        ).inheritIO().start().waitFor()
        check(made == 0) { "keytool could not make the debug key at $store" }
    }
}
