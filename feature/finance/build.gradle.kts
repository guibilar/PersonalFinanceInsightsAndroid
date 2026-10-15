// The finance module: its screens, its navigation graph (financeGraph), its notifications and its widget.
// It doesn't depend on the app: what it needs from it comes through FinanceHost, and the Documents pages through
// FinanceDocuments (the Documents API comes from :core:documents; never a dependency on :feature:documents).
plugins {
    alias(libs.plugins.hestia.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.guibilar.financeinsights.finance"
}

dependencies {
    api(project(":core:documents"))
    api(project(":core:finance"))
    // The "Needs attention" items (GET /api/alerts, T67) and the one link map.
    api(project(":core:today"))
    api(project(":core:ui"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core:finance")))
    testImplementation(testFixtures(project(":core:documents")))
    testImplementation(testFixtures(project(":core:today")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.glance.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
