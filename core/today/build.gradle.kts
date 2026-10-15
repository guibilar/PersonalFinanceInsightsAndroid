// Today without Android: the models of `GET /api/today` and `GET /api/alerts`, their Retrofit interfaces, the filler that
// puts the app's money, percent and date formats into the card texts, and the one map from a card's link to a page of
// the app. The server owns the card and alert rules (Hestia T08, T15); the app only formats.
// Plain Kotlin, tested on the JVM. Its test fixtures hold the fake TodayApi and AlertsApi, and sample answers.
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
