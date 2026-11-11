plugins { id("recorra.spring-boot-app-conventions") }

dependencies {
  // Única ponte core/driver: o contrato canônico gerado (ADR-0001).
  implementation(project(":contracts"))
}
