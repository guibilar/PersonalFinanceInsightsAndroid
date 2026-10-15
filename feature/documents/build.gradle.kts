// The Documents module: its list, detail and upload screens, the document capture (scanner, photos, files), and its
// navigation graph (documentsGraph). It depends on the core modules only, never on another feature module or the app
// (D14): the transactions of its links come from the finance API in :core:finance.
plugins {
    alias(libs.plugins.hestia.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.guibilar.financeinsights.documents"
}

dependencies {
    api(project(":core:documents"))
    api(project(":core:finance"))
    // The "Tasks" section of a document (T84): the Tasks API only, never :feature:tasks (D14).
    api(project(":core:tasks"))
    // The "Items" section of a document (T88): the Home API only, never :feature:home (D14).
    api(project(":core:home"))
    api(project(":core:ui"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.document.scanner)
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
