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
        // Репозиторій, де публікується NewPipeExtractor
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SonicSnag"
include(":app")
