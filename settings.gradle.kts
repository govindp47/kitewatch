pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "kitewatch"

// Application
include(":app")

// Feature modules
include(":feature:portfolio")
include(":feature:holdings")
include(":feature:orders")
include(":feature:transactions")
include(":feature:gtt")
include(":feature:settings")
include(":feature:onboarding")
include(":feature:auth")

// Core modules
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:ui")

// Infra modules
include(":infra:worker")
include(":infra:auth")
include(":infra:backup")
include(":infra:csv")
