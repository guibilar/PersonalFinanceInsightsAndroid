// What every module shares and that needs nothing from Android: amounts and days as the app shows them.
// Plain Kotlin, tested on the JVM.
plugins {
    alias(libs.plugins.hestia.jvm.library)
}

dependencies {
    testImplementation(libs.junit)
}
