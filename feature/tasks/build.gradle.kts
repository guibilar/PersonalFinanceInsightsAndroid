// The Tasks module: the Tasks screen (quick add, sections, done and undo), the task sheet (details, repeat, links,
// completions), and its navigation graph (tasksGraph). It depends on the core modules only, never on another feature
// module or the app (D14): the linked documents come from :core:documents, the linked transactions from :core:finance.
plugins {
    alias(libs.plugins.hestia.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.guibilar.financeinsights.tasks"
}

dependencies {
    api(project(":core:tasks"))
    // The items of a task (T88): the Home API only, never :feature:home (D14).
    api(project(":core:home"))
    // The people of a task (T93): the People API; never :feature:people (D14).
    api(project(":core:people"))
    api(project(":core:documents"))
    api(project(":core:finance"))
    api(project(":core:ui"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core:network")))
    testImplementation(testFixtures(project(":core:finance")))
    testImplementation(testFixtures(project(":core:documents")))
    testImplementation(testFixtures(project(":core:tasks")))
    testImplementation(testFixtures(project(":core:home")))
    testImplementation(testFixtures(project(":core:people")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
