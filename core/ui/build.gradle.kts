// What every module's screens share: the theme, common components and charts, and the settings page's frame.
plugins {
    alias(libs.plugins.hestia.android.compose)
}

android {
    namespace = "com.guibilar.financeinsights.core.ui"
}

dependencies {
    api(project(":core:common"))
    api(project(":core:network"))
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    // The window's theme (Theme.Finance) is a splash screen theme.
    implementation(libs.androidx.core.splashscreen)
}
