package com.recorra.driver.bling.qa;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

/** QA (REC-003): o driver também expõe só health/info/prometheus. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ActuatorExposicaoTest {

  @Autowired RestTestClient client;

  @ParameterizedTest
  @ValueSource(strings = {"env", "beans", "configprops", "mappings", "heapdump"})
  void endpointSensivelNaoExposto(String endpoint) {
    client.get().uri("/actuator/" + endpoint).exchange().expectStatus().isNotFound();
  }
}
