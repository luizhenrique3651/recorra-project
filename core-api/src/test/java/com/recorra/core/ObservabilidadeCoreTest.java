package com.recorra.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.common.logging.CapturaDeLogJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * REC-005 no core: JSON com traceId, handler RFC 7807 e mascaramento ligados pela config do app.
 */
class ObservabilidadeCoreTest extends IntegrationTestBase {

  private static final Logger LOG = LoggerFactory.getLogger(ObservabilidadeCoreTest.class);
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
          .satisfies(
              e -> assertThat(e).containsEntry("traceId", TRACE).doesNotContainKey("stack_trace"));
    }
  }

  @Test
  @DisplayName(
      "Dado um log com telefone, nome, CPF, e-mail e token / Quando o appender do core escreve / Então todos saem mascarados")
  void appenderDoCoreMascaraPii() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      LOG.info(
          "cliente nome=Maria Souza telefone=11987654321 cpf=12345678909 email=maria@x.com"
              + " Authorization: Bearer segredo-bearer-123");

      assertThat(logs.texto())
          .doesNotContain("Maria", "Souza", "11987654321", "12345678909", "maria@x.com")
          .doesNotContain("segredo-bearer-123");
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
