package com.recorra.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/** QA (REC-003): só health/info/prometheus; env/beans/configprops vazariam config e credenciais. */
class ActuatorExposicaoCoreTest extends IntegrationTestBase {

  @Autowired RestTestClient client;

  @ParameterizedTest
  @ValueSource(strings = {"env", "beans", "configprops", "mappings", "heapdump", "flyway"})
  @DisplayName("Dado a exposição restrita / Quando GET /actuator/<sensível> / Então 404")
  void endpointSensivelNaoExposto(String endpoint) {
    client.get().uri("/actuator/" + endpoint).exchange().expectStatus().isNotFound();
  }

  @Test
  @DisplayName("Dado o Prometheus / Quando leio as métricas / Então sem senha do banco no corpo")
  void prometheusNaoVazaCredencial() {
    client
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(
            corpo -> {
              org.assertj.core.api.Assertions.assertThat(corpo)
                  .doesNotContain(IntegrationTestBase.POSTGRES.getPassword());
            });
  }
}
