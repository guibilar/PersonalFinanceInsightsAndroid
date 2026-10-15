// Documents without Android: the models of the Documents API of Hestia.Api (`/api/documents`), the Retrofit interface,
// the upload form (multipart), and the upload checks (size and type). Plain Kotlin, tested on the JVM.
// Its test fixtures hold the fake DocumentsApi and sample documents.
plugins {
    alias(libs.plugins.hestia.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

dependencies {
    api(project(":core:common"))
    api(project(":core:network"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
