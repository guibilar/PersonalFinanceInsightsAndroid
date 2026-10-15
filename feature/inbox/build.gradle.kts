// The Inbox (T18): quick notes kept to sort later, from `/api/inbox` of Hestia.Api (its API and models are here). A
// note can go at once, or later, to a task (:core:tasks) or to today's journal entry (:core:journal). It depends on the
// core modules only, never on another feature module or the app (D14).
plugins {
    alias(libs.plugins.hestia.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.guibilar.financeinsights.inbox"
}

dependencies {
    api(project(":core:network"))
    api(project(":core:ui"))
    api(project(":core:tasks"))
    api(project(":core:journal"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core:network")))
    testImplementation(testFixtures(project(":core:tasks")))
    testImplementation(testFixtures(project(":core:journal")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
