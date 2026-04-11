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

rootProject.name = "finalis-mobile-android"
include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:crypto",
    ":core:wallet",
    ":core:storage",
    ":core:testing",
    ":data:lightserver",
    ":data:wallet",
    ":feature:onboarding",
    ":feature:home",
    ":feature:receive",
    ":feature:send",
    ":feature:history",
    ":feature:settings",
    ":sync",
    ":benchmark"
)
