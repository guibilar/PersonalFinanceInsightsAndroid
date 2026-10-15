import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// The shared settings of the app's modules (T30). A module's build file names its plugin, its namespace (Android), and
// its dependencies; nothing else.

/** Java 17 bytecode, built with JDK 21, as the app. */
private const val JDK = 21
private val BYTECODE = JavaVersion.VERSION_17

/** A plain Kotlin module (`:core:*` without Android): JDK 21 toolchain, Java 17 bytecode. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(JDK)
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = BYTECODE
            targetCompatibility = BYTECODE
        }
    }
}

/** An Android library: compile SDK 37.2, min SDK 26, Java 17, lint with warnings as errors. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk {
                version = release(37) { minorApiLevel = 2 }
            }
            defaultConfig.minSdk = 26
            compileOptions {
                sourceCompatibility = BYTECODE
                targetCompatibility = BYTECODE
            }
            lint {
                // A new warning fails the build, as in the app.
                warningsAsErrors = true
                disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
            }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            jvmToolchain(JDK)
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
}

/** Compose on an Android library. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("hestia.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
    }
}

/**
 * A feature module: an Android library with Compose, and the Robolectric screenshot tests (Android resources, the
 * JDK 17+ opens, Roborazzi recording). The tests write build/shots, which is not a declared output, so they are never
 * taken from the build cache (T27).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("hestia.android.compose")
        extensions.configure<LibraryExtension> {
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    all {
                        it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
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
        }
        tasks.withType<Test>().configureEach {
            outputs.doNotCacheIf("The screenshot tests write build/shots, which is not a declared output") { true }
        }
    }
}
