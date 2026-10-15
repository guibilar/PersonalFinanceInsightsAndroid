// Reaching the server: the HTTP client with its cookie jar, the server's address, and what a failed call means.
// Also signing in (AuthApi) and the signed-in client that every module's API shares (ApiClient).
// Plain Kotlin, tested on the JVM. Its test fixtures hold the fake AuthApi.
plugins {
    alias(libs.plugins.hestia.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

dependencies {
    // DataChanges: a successful write through the client says that the data changed (T58).
    implementation(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.retrofit)
    api(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
