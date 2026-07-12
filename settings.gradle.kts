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

rootProject.name = "AccessibleToolkit"

include(":core:engine")
include(":core:vosk")
include(":core:vad")
include(":service:accessibility")
include(":feature:subtitle")
include(":feature:voice")
include(":feature:elder")
include(":feature:bridge")
include(":app")