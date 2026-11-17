plugins { id("recorra.spring-boot-app-conventions") }

dependencies {
  // Única ponte core/driver: o contrato canônico gerado (ADR-0001).
  implementation(project(":contracts"))
  // Kit dos drivers (REC-004): cliente resiliente, publisher, poller, webhook, idempotência.
  implementation(project(":drivers:driver-sdk"))

  // CapturaDeLogJson: teste de appender (REC-005).
  testImplementation(testFixtures(project(":common")))
}
