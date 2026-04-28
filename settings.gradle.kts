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

rootProject.name = "phone_app_agent"

include(
    ":app",
    ":data-history",
    ":core-dsl",
    ":core-policy",
    ":core-runner",
    ":runtime-litertlm",
    ":driver-accessibility",
    ":overlay-ui",
    ":skill-registry",
)

