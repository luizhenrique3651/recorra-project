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

// ---- JaCoCo: relatório + verificação de 80% de linha ----
// Fora da base do relatório e do gate:
//  * classes *Application (só o main do Spring);
//  * código gerado (tudo cujo .java está sob build/generated/, ex.: jsonschema2pojo do contracts):
//    não é escrito por nós, não se testa, e entraria na base como cobertura falsa ou como buraco.
val dirGerado = layout.buildDirectory.dir("generated")

fun classesDoCodigoGerado(raizes: Set<File>, gerado: File): List<String> =
    raizes
        .filter { it.exists() && it.startsWith(gerado) }
        .flatMap { raiz ->
          raiz
              .walkTopDown()
              .filter { it.isFile && it.extension == "java" }
              .map { it.relativeTo(raiz).path.removeSuffix(".java").replace(File.separatorChar, '/') }
              .toList()
        }

val classesCobertas =
    sourceSets.main.map { main ->
      val geradas = classesDoCodigoGerado(main.java.srcDirs, dirGerado.get().asFile)
      main.output.asFileTree.matching {
        exclude("**/*Application.class")
        geradas.forEach { exclude("$it.class", "$it\$*.class") }
      }
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

// Sem vacuidade: o JaCoCo PULA a verificação quando não há dados de execução ("missing execution
// data"), então um módulo com classes de produção e zero testes passaria sem ninguém notar. Aqui
// isso FALHA o build. Módulo sem classes de produção (só package-info, só *Application) passa.
val verificarDadosDeCobertura =
    tasks.register("verificarDadosDeCobertura") {
      group = "verification"
      description = "Falha se há classes de produção e nenhum dado de execução do JaCoCo."
      dependsOn(tasks.test)
      val classes = classesCobertas
      val execucao = tasks.test.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile }
      val modulo = project.path
      doLast {
        val temClassesDeProducao = classes.get().files.any { it.name.endsWith(".class") }
        val arquivo = execucao.get()
        if (temClassesDeProducao && (!arquivo.exists() || arquivo.length() == 0L)) {
          throw GradleException(
              "Módulo $modulo tem classes de produção, mas nenhum dado de execução do JaCoCo " +
                  "(nenhum teste rodou). Sem isso o gate de cobertura seria vacuamente verde: " +
                  "escreva os testes do módulo.")
        }
      }
    }

tasks.check {
  dependsOn(verificarDadosDeCobertura)
  dependsOn(tasks.jacocoTestCoverageVerification)
}
