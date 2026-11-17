plugins { id("recorra.library-conventions") }

dependencies {
  // Os eventos publicados são os tipos gerados do contrato (única ponte core/driver, ADR-0001).
  api(project(":contracts"))

  // WebClient (só o spring-webflux, sem starter: não troca o servidor dos apps; o conector é o
  // HttpClient do JDK) e os tipos do Resilience4j que aparecem na API pública (circuitBreaker() e
  // rateLimiter()).
  api(libs.spring.webflux)
  api(libs.resilience4j.circuitbreaker)
  api(libs.resilience4j.ratelimiter)

  // Detalhe de implementação: não vazam na API.
  implementation(libs.resilience4j.retry)
  implementation(libs.resilience4j.reactor)
  implementation(libs.slf4j.api)

  testImplementation(libs.wiremock)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.logback.classic)
}
