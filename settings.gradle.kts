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

rootProject.name = "Heretic"
include(":app")
// Vendorowany kontrakt wejścia dla bibliotek natywnych (com.beloko.*, com.mobeta.*) —
// biblioteki te wołają te klasy Java po imieniu. Produktowe sterowanie dotykowe
// żyje w app/src/main/java/com/heretic/app/touch i nie zależy od tego modułu.
include(":opentouch-input")
