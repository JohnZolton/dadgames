plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    testLogging { showStandardStreams = true; events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.test {
    if (project.hasProperty("tournament")) systemProperty("tournament", "1")
    project.findProperty("games")?.let { systemProperty("games", it.toString()) }
    outputs.upToDateWhen { false }
}
