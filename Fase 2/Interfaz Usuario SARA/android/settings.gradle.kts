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
        //Repositorio JitPack para la librería USB esto faltaba!
        maven { url = uri("https://jitpack.io") } 
    }
}

rootProject.name = "SaraRobot"
include(":app")

