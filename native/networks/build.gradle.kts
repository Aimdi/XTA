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

// The other networks XTA reads, beside X: one package each, plain Kotlin like :core.
dependencies {
    api(project(":core"))
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The live job asks for *LiveTest in every module; a network may not have one yet.
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
