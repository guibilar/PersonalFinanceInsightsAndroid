// Finance without Android: the finance API's models and client, and the logic its screens share (date presets,
// dashboard figures, alerts, validation). Plain Kotlin, so it is tested on the JVM in seconds.
// Its test fixtures (the fake API with responses recorded from a real backend) serve the Android modules' tests too.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
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
    api(project(":core:common"))
    api(project(":core:network"))
    // @Keep, for R8 in the app's minified build.
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    // The fake API delays some answers (kotlinx.coroutines.delay).
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
