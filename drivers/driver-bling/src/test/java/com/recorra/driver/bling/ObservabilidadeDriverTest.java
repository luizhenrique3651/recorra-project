package com.recorra.driver.bling;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.common.logging.CapturaDeLogJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.client.RestTestClient;

/** REC-005 no driver: mesma base de logs do core (JSON, traceId, RFC 7807, mascaramento). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ObservabilidadeDriverTest {

  private static final Logger LOG = LoggerFactory.getLogger(ObservabilidadeDriverTest.class);
  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

  @Autowired RestTestClient client;
  @Autowired Environment env;

  @Test
  @DisplayName(
      "Dado uma rota inexistente com traceparent / Quando processada / Então o ProblemDetail e o log JSON têm o traceId")
  void problemDetailComTraceId() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/nao/existe")
          .header("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-01")
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.traceId")
          .isEqualTo(TRACE);

      assertThat(logs.eventos("WARN"))
          .singleElement()
          .satisfies(e -> assertThat(e).containsEntry("traceId", TRACE));
    }
  }

  @Test
  @DisplayName(
      "Dado o callback OAuth do Bling logado / Quando o appender escreve / Então code e state saem mascarados")
  void appenderDoDriverMascaraOauthEPii() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      LOG.info(
          "GET /driver/v1/bling/callback?code=abc123&state=xyz789 nome=Joana Lima cpf=12345678909");

      assertThat(logs.texto()).doesNotContain("abc123", "xyz789", "Joana", "Lima", "12345678909");
    }
  }

  @Test
  @DisplayName(
      "Dado o app sem import manual / Quando leio o Environment / Então JSON, mascaramento e access log desligado já vêm do common")
  void configDeLogVemDoCommonSemImportManual() {
    assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    assertThat(env.getProperty("logging.structured.json.customizer"))
        .isEqualTo("com.recorra.common.logging.MascaramentoJsonCustomizer");
    assertThat(env.getProperty("server.tomcat.accesslog.enabled")).isEqualTo("false");
  }
}
