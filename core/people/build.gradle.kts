// People without Android: the models of the People API of Hestia.Api (`/api/people`), the Retrofit interface, and the rules
// of the People screens (the reminder, the birthdays, the person form, the labels). Plain Kotlin, tested on the JVM.
// Its test fixtures hold the fake PeopleApi and sample people.
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
