// The Journal without Android: the models of the Journal API of Hestia.Api (`/api/journal`, T21), the Retrofit
// interface, and the rules of the Journal screen (the day labels, the streak, "On this day"). Plain Kotlin, tested on
// the JVM. Its test fixtures hold the fake JournalApi and sample entries.
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
