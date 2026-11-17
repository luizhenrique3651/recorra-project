plugins { id("recorra.contracts-conventions") }

dependencies {
  // As classes geradas usam as anotações com.fasterxml.jackson.annotation (válidas no Jackson 3).
  api(libs.jackson.annotations)

  // Testes de round-trip com o Jackson 3, o mesmo do Spring Boot 4.
  testImplementation(libs.jackson.databind)

  // Validação dos schemas contra o meta-schema e das fixtures contra os schemas (ADR-0001 item 3,
  // REC-003). Roda no `test`, então o CI e o desenvolvedor executam exatamente a mesma validação.
  testImplementation(libs.json.schema.validator)
}

// Testes sem Spring: root em INFO (gradle/logback-test.xml), sem o DEBUG padrão do logback.
tasks.processTestResources { from(rootProject.layout.projectDirectory.file("gradle/logback-test.xml")) }
