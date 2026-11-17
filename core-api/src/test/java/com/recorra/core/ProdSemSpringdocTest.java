package com.recorra.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Débito REC-001 (a): no perfil prod o contrato OpenAPI e o Swagger UI não são expostos. */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    // O perfil prod exige as variáveis de ambiente; valores só de teste. A URL inválida é
    // descartada: o @ServiceConnection abaixo substitui a conexão pelo container real.
    properties = {"DB_URL=jdbc:postgresql://invalido/x", "DB_USER=teste", "DB_PASSWORD=teste"})
@AutoConfigureRestTestClient
@ActiveProfiles("prod")
class ProdSemSpringdocTest {

  @ServiceConnection static final PostgreSQLContainer POSTGRES = IntegrationTestBase.POSTGRES;

  @Autowired RestTestClient client;

  @Test
  @DisplayName("Dado o perfil prod / Quando GET /v3/api-docs / Então 404")
  void apiDocsDesligado() {
    client.get().uri("/v3/api-docs").exchange().expectStatus().isNotFound();
  }

  @Test
  @DisplayName("Dado o perfil prod / Quando GET /swagger-ui.html / Então 404")
  void swaggerUiDesligado() {
    client.get().uri("/swagger-ui.html").exchange().expectStatus().isNotFound();
  }
}
