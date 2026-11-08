// Convenções comuns a TODO módulo Java do monorepo:
// toolchain 21, BOM do Spring Boot, JUnit, Spotless, SpotBugs e JaCoCo (DoD: cobertura >= 80%).

import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask

plugins {
  java
  jacoco
  id("com.diffplug.spotless")
  id("com.github.spotbugs")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = libs.findLibrary(alias).get()

group = "com.recorra"

version = "0.1.0-SNAPSHOT"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
  }
}

dependencies {
  implementation(platform(lib("spring-boot-bom")))
  annotationProcessor(platform(lib("spring-boot-bom")))
  testImplementation(platform(lib("spring-boot-bom")))

  testImplementation(lib("junit-jupiter"))
  testImplementation(lib("assertj-core"))
  testRuntimeOnly(lib("junit-platform-launcher"))
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
  options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

// ---- Formatação (DoD: sem violação de Spotless) ----
spotless {
  java {
    target("src/**/*.java")
    googleJavaFormat()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

// ---- SpotBugs: quebra o build só em achados de alta confiança ----
spotbugs {
  effort = Effort.DEFAULT
  reportLevel = Confidence.HIGH
  ignoreFailures = false
}

tasks.withType<SpotBugsTask>().configureEach {
  reports.create("html") { required = true }
}

tasks.named("spotbugsTest") { enabled = false }

// ---- JaCoCo: relatório + verificação de 80% de linha (classes *Application ficam fora) ----
val classesCobertas =
    sourceSets.main.map { main ->
      main.output.asFileTree.matching { exclude("**/*Application.class") }
    }

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  classDirectories.setFrom(classesCobertas)
  reports {
    xml.required = true
    html.required = true
  }
}

tasks.jacocoTestCoverageVerification {
  dependsOn(tasks.test)
  classDirectories.setFrom(classesCobertas)
  violationRules {
    rule {
      limit {
        counter = "LINE"
        minimum = "0.80".toBigDecimal()
      }
    }
  }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
