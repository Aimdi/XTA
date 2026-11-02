// XTA Native: a prototype of XTA written for Android alone, beside the Flutter app.
//
// :core is plain Kotlin (the X client, the request signer, the parsers, the
// backup import) and builds anywhere with a JDK. :app is the Android app; it
// joins the build only where an Android SDK is installed.

pluginManagement {
    repositories {
        // Maven Central through repo1: repo.maven.apache.org answers some
        // networks with 429.
        maven("https://repo1.maven.org/maven2")
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
        maven("https://repo1.maven.org/maven2")
    }
}

rootProject.name = "xta-native"

include(":core")

val androidSdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (androidSdk != null || file("local.properties").exists()) {
    include(":app")
}
