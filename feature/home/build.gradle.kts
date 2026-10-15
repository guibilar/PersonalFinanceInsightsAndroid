// The Home module: the Home screen (maintenance, inventory, rooms, search, the export for insurance), a room, an item
// (details, warranty, photos, links, maintenance), the item form (with a photo), and its navigation graph (homeGraph).
// It depends on the core modules only, never on another feature module or the app (D14): documents come from
// :core:documents, transactions from :core:finance, maintenance tasks from :core:tasks. The app opens the pages of the
// other modules and scales photos through HomeHost.
plugins {
    alias(libs.plugins.hestia.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.guibilar.financeinsights.home"
}

dependencies {
    api(project(":core:home"))
    api(project(":core:tasks"))
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
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
