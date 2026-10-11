// Reaching the server: the HTTP client with its cookie jar, the server's address, and what a failed call means.
// Every module's API is built on it. Plain Kotlin, tested on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    api(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.junit)
}
