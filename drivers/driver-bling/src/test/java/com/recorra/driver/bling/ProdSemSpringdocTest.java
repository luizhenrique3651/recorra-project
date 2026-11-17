package com.recorra.driver.bling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Débito REC-001 (a): no perfil prod o contrato OpenAPI e o Swagger UI não são expostos. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("prod")
class ProdSemSpringdocTest {

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
