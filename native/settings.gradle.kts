// XTA Native: XTA written in Kotlin, for Android and the Linux desktop. It
// replaced XTA's Flutter app, which history keeps up to commit fab7619b.
//
// :core is plain Kotlin (the X client, the request signer, the parsers, the
// backup import) and builds anywhere with a JDK; so do :networks, the other
// networks XTA reads, :store, :services and :plugin-host. :ui holds the screens
// both apps share (Compose Multiplatform) and :desktop is the Linux app; both
// build with a JDK alone. :app is the Android app; it, and the Android half of
// :ui, join the build only where an Android SDK is installed.

pluginManagement {
    repositories {
        // Maven Central through Google's mirror: repo1.maven.org answered some
        // artifacts with a redirect to github.com, which returned 504 (2026-11-08).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroup("com.google.testing.platform")
            }
        }
        maven("https://maven-central.storage-download.googleapis.com/maven2")
    }
}

rootProject.name = "xta-native"

include(":core")
include(":networks")
include(":store")
include(":services")
include(":plugin-host")
include(":ui")
include(":desktop")

// The stand-in for X the emulator and desktop checks read (tool/mock-x/README.md): a tool, in no app.
include(":mock-x")
project(":mock-x").projectDir = file("tool/mock-x")

// The downloadable plugins (PLUGINS.md): every folder of plugins/ with a build
// file is one, :plugins:<id>, configured by plugins/build.gradle.kts.
file("plugins").listFiles().orEmpty()
    .filter { File(it, "build.gradle.kts").isFile }
    .sortedBy { it.name }
    .forEach { include(":plugins:${it.name}") }

val androidSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (androidSdk != null || file("local.properties").exists()) {
    include(":app")
}
