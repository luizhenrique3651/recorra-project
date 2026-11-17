package com.recorra.driver.bling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class DriverBlingApplicationSmokeTest {

  @Autowired RestTestClient client;

  @Test
  void contextoSobe() {
    // Falha se o contexto Spring não subir.
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
  @DisplayName("Dado o perfil default / Quando GET /v3/api-docs / Então o contrato está exposto")
  void springdocLigadoForaDeProd() {
    client.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
  }
}
