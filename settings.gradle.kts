pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PersonalFinanceInsights"
include(
    ":core:common", ":core:network", ":core:finance", ":core:today", ":core:documents", ":core:tasks", ":core:home", ":core:people", ":core:ui",
    ":feature:finance", ":feature:documents", ":feature:tasks", ":feature:home", ":feature:people", ":app",
)
