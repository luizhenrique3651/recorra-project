package com.recorra.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

class CoreApiApplicationSmokeTest extends IntegrationTestBase {

  @Autowired RestTestClient client;

  @Test
  void contextoSobe() {
    // Falha se o contexto Spring (incluindo Flyway + validação do schema) não subir.
  }

  @Test
  void healthRespondeUp() {
    client
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }
}
