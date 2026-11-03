pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "CleanShot"

// Pure Kotlin/JVM modules (no Android dependency).
include(":domain")
include(":processing")

// Android modules.
include(":app")
include(":core:designsystem")
include(":core:common")
include(":data")
include(":feature")
