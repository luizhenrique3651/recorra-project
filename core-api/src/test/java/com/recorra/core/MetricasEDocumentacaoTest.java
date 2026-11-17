package com.recorra.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

class MetricasEDocumentacaoTest extends IntegrationTestBase {

  @Autowired RestTestClient client;

  @Test
  @DisplayName("Dado a app no ar / Quando GET /actuator/prometheus / Então expõe métricas")
  void prometheusExpoeMetricas() {
    client
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(corpo -> assertThat(corpo).contains("jvm_memory_used_bytes"));
  }

  @Test
  @DisplayName("Dado o perfil test / Quando GET /v3/api-docs / Então o contrato está exposto")
  void springdocLigadoForaDeProd() {
    client.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
  }
}
