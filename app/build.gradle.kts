plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.guibilar.financeinsights"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.guibilar.hestia"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "0.18.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key until there is a release key: installable straight from the CI artifact.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // The phone's time zone in the tests: the same on every machine (Today's greeting reads the hour).
                it.systemProperty("user.timezone", "UTC")
                // Robolectric reaches into these on JDK 17+.
                it.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
                // Screenshots are recorded on every run (they are for looking at, not compared).
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }

    lint {
        // A new warning fails the build. The baseline holds the one left on purpose: raising targetSdk changes how
        // the app behaves, so it is a change of its own.
        warningsAsErrors = true
        baseline = file("lint-baseline.xml")
        // Newer library versions come as Dependabot pull requests, not as lint failures on unrelated changes.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

// The screenshot tests write build/shots, which is not a declared output. A test result from the build cache
// does not bring these images back, so the tests always run (T27).
tasks.withType<Test>().configureEach {
    outputs.doNotCacheIf("The screenshot tests write build/shots, which is not a declared output") { true }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":feature:finance"))
    implementation(project(":feature:documents"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:home"))
    implementation(project(":feature:people"))
    implementation(project(":feature:journal"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:search"))
    implementation(project(":feature:inbox"))
    implementation(project(":core:today"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    // The alert notifications poll in the background (T68).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core:network")))
    testImplementation(testFixtures(project(":core:finance")))
    testImplementation(testFixtures(project(":core:today")))
    testImplementation(testFixtures(project(":core:documents")))
    testImplementation(testFixtures(project(":core:tasks")))
    testImplementation(testFixtures(project(":core:home")))
    testImplementation(testFixtures(project(":core:people")))
    testImplementation(testFixtures(project(":core:journal")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
