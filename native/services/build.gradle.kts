plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// What XTA Native does with what it read (articles, links, translation, text for
// speech): plain Kotlin like :core, so the Android and desktop apps share it.
dependencies {
    api(project(":core"))
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The live job asks for *LiveTest in every module; this one may have none.
    filter { isFailOnNoMatchingTests = false }
    // RUN_LIVE=true lets each network's *LiveTest read the live network (the native workflow's live job).
    val live = System.getenv("RUN_LIVE") ?: "false"
    environment("RUN_LIVE", live)
    inputs.property("live", live)
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = live == "true"
    }
}
