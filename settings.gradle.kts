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

rootProject.name = "game_for_my_kids"

include(":hub")
include(":connect")
include(":games:color_block:game-core")
include(":games:color_block:ui")
include(":games:beach_volleyball:game-core")
include(":games:beach_volleyball:ui")
