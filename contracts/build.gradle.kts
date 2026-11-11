plugins { id("recorra.contracts-conventions") }

dependencies {
  // As classes geradas usam as anotações com.fasterxml.jackson.annotation (válidas no Jackson 3).
  api(libs.jackson.annotations)

  // Testes de round-trip com o Jackson 3, o mesmo do Spring Boot 4.
  testImplementation(libs.jackson.databind)
}
