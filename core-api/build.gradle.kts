plugins { id("recorra.spring-boot-app-conventions") }

dependencies {
  // Única ponte core/driver: o contrato canônico gerado (ADR-0001).
  implementation(project(":contracts"))

  // Persistência (REC-003): JPA + Flyway; o schema é dono das migrations (ddl-auto=validate).
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.flyway)
  implementation(libs.flyway.database.postgresql)
  runtimeOnly(libs.postgresql)

  // Integração: Testcontainers 2.x (Postgres) + convenção de migrations compartilhada.
  testImplementation(libs.spring.boot.testcontainers)
  testImplementation(libs.testcontainers.junit.jupiter)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(testFixtures(project(":common")))
}

tasks.test {
  // Postgres de teste reutilizável entre execuções locais (Docker de 4 GB compartilhado): o
  // container fica de pé e a próxima execução o reaproveita. No CI (CI=true) não se reutiliza.
  // Para liberar a memória: docker rm -f $(docker ps -aq --filter label=org.testcontainers=true --filter ancestor=postgres:16-alpine)
  // ATENÇÃO: maxParallelForks > 1 com reuse faz os forks dividirem o MESMO banco (migrations e dados
  // de um fork aparecem no outro). Mantenha 1 fork ou desligue o reuso.
  if (System.getenv("CI") == null) {
    environment("TESTCONTAINERS_REUSE_ENABLE", "true")
  }
}
