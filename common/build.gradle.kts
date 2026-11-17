plugins {
  id("recorra.library-conventions")
  `java-test-fixtures`
}

dependencies {
  // Infra de observabilidade (REC-005). Spring e Logback são compileOnly: a lib não impõe nada; os
  // apps (webmvc + Boot) já trazem os dois. Sem eles no classpath, só as classes puras (Traceparent,
  // MDC, mascaramento) funcionam.
  compileOnly(libs.spring.boot.starter.webmvc)
  compileOnly(libs.logback.classic)
  compileOnly(libs.reactor.core)
  implementation(libs.slf4j.api)

  // Captura de logs JSON pelo mesmo encoder do console (usada também por core-api e driver-bling).
  testFixturesImplementation(platform(libs.spring.boot.bom))
  testFixturesImplementation(libs.spring.boot.starter.test)
  testFixturesImplementation(libs.logback.classic)
  testFixturesImplementation(libs.jackson.databind)

  testImplementation(libs.spring.boot.starter.webmvc)
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.spring.boot.starter.webmvc.test)
  testImplementation(libs.spring.boot.resttestclient)
  testImplementation(libs.logback.classic)
  testImplementation(libs.reactor.core)
}
