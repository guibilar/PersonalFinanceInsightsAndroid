// The Calendar without Android: the models of the Calendar API of Hestia.Api (`/api/calendar`, T103), the Retrofit
// interface, and the rules of the Calendar screen (the month grid, the labels, the repeats). Plain Kotlin, tested on the
// JVM. Its test fixtures hold the fake CalendarApi and sample items.
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
