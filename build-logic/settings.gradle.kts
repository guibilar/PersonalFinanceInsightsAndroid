// The convention plugins of the app's modules (T30): the shared Android, Kotlin, lint, and test settings in one place.
// The versions stay in the app's version catalogue.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
