pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = java.net.URI("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
    }
}

rootProject.name = "dsh-android"

// Core
include(":core:model")
include(":core:protocol")
include(":core:bridge")
include(":core:network")
include(":core:data")
include(":core:designsystem")
include(":core:ui")

// Features
include(":feature:runtime")
include(":feature:hosts")
include(":feature:sessions")
include(":feature:chat")
include(":feature:approval")
include(":feature:settings")
include(":feature:files")

// Plugin System
include(":plugin:runtime-web")
include(":plugin:surface")
include(":plugin:mobile-ui")

// Runtime & Bridge
include(":runtime:host")
include(":runtime:bridge")

// Tooling
include(":tooling:codegen")
include(":tooling:contract")
include(":tooling:frontend-patch")

// App
include(":app")

