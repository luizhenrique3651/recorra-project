pluginManagement { includeBuild("build-logic") }

plugins {
  // Baixa o JDK 21 automaticamente se a máquina não tiver um (toolchain).
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories { mavenCentral() }
}

rootProject.name = "recorra"

include(
    "contracts",
    "common",
    "core-api",
    "drivers:driver-sdk",
    "drivers:driver-bling",
    "messaging:wa-cloud",
    "messaging:wa-terceiro",
)
