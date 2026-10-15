// Tasks without Android: the models of the Tasks API of Hestia.Api (`/api/tasks`), the Retrofit interface, and the
// rules of the Tasks screen (sections, labels, the repeat form). Plain Kotlin, tested on the JVM.
// Its test fixtures hold the fake TasksApi and sample tasks.
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
