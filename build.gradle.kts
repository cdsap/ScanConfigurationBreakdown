plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.fatbinary)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.geapi.data)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    implementation(libs.clikt)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("io.github.cdsap.MainKt")
    applicationName = "scb"
}

fatBinary {
    // The plugin appends the Kotlin file-class suffix itself: this becomes io.github.cdsap.MainKt.
    mainClass = "io.github.cdsap.Main"
    name = "scb"
}

tasks.test {
    useJUnitPlatform()
}

// FatBinary's fatJar writes into build/libs, the same directory the application plugin's
// startScripts reads, so Gradle flags the undeclared ordering whenever both are in one task
// graph (`./gradlew build fatBinary`). Declaring it keeps the two plugins co-existing.
listOf("startScripts", "distTar", "distZip", "installDist").forEach { task ->
    tasks.named(task) { dependsOn("fatJar") }
}
