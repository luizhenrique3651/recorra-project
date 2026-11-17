// Testes de arquitetura (REC-004): enxergam as classes de produção de TODOS os módulos de uma vez e
// impedem o vazamento core <-> driver. Sem código de produção próprio.

plugins { id("recorra.java-conventions") }

dependencies {
  testImplementation(libs.archunit)
  // Só para a fixture do "caminho permitido" (uma classe do core usando o contrato gerado).
  testImplementation(project(":contracts"))
}

// Módulos analisados: TODOS os subprojetos com plugin java (menos este). O teste importa as classes
// compiladas de produção (build/classes/java/main) de cada um; módulo novo entra sozinho.
val aqui = project
rootProject.subprojects
    .filter { it != aqui }
    .forEach { modulo ->
      modulo.plugins.withId("java") {
        val chave = modulo.path.removePrefix(":").replace(':', '.')
        val classes = modulo.layout.buildDirectory.dir("classes/java/main")
        tasks.test {
          // Input rastreado + dependência: recompilou o módulo, as regras rodam de novo.
          inputs.dir(classes).withPropertyName("classes-$chave").optional()
          dependsOn(modulo.tasks.named("classes"))
          systemProperty("recorra.classes.$chave", classes.get().asFile.absolutePath)
        }
      }
    }

// Testes sem Spring: root em INFO (gradle/logback-test.xml), sem o DEBUG padrão do logback.
tasks.processTestResources { from(rootProject.layout.projectDirectory.file("gradle/logback-test.xml")) }
