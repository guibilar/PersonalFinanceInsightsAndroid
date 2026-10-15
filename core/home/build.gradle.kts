// Home without Android: the models of the Home API of Hestia.Api (`/api/home`), the Retrofit interface, and the rules
// of the Home screens (the warranty state, the item form, the labels). Plain Kotlin, tested on the JVM.
// Its test fixtures hold the fake HomeApi and sample rooms and items.
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
