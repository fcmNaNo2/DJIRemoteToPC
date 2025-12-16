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
        // DJI 官方 Maven 仓库
        maven { url = uri("https://developer.dji.com/maven2") }
        maven { url = uri("https://artifact.bytedance.com/repository/pangle") }
    }
}

rootProject.name = "DJIRemoteToPC"
include(":app")
