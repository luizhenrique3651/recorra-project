package com.recorra.common.qa;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.common.logging.CapturaDeLogJson;
import com.recorra.common.teste.AppDeTeste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/** QA (REC-005): o appender mascarador nunca pode derrubar/perder um evento de log. */
@SuppressWarnings("try")
// Mesma configuração do ObservabilidadeWebTest: reaproveita o contexto em cache (um segundo
// contexto distinto reinstala hooks globais do Reactor e quebra aquele teste).
@SpringBootTest(classes = AppDeTeste.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class QaAppenderTest {

  private static final Logger LOG = LoggerFactory.getLogger(QaAppenderTest.class);

  @Test
  @DisplayName(
      "Dado um log com Marker / Quando o appender escreve / Então o evento sai (não se perde)")
  void eventoComMarkerNaoSePerde() {
    Marker marcador = MarkerFactory.getMarker("AUDITORIA");
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      LOG.info(marcador, "linha com marker nome=Maria Silva");

      assertThat(logs.eventos()).singleElement();
      assertThat(logs.texto()).contains("linha com marker").doesNotContain("Maria");
    }
  }

  @Test
  @DisplayName("Dado um log com argumentos estruturados (KV) / Quando escreve / Então o evento sai")
  void eventoComKeyValueNaoSePerde() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      LOG.atInfo().addKeyValue("cpf", "12345678909").addKeyValue("x", 1).log("com kv");

      assertThat(logs.eventos()).singleElement();
      assertThat(logs.texto()).doesNotContain("12345678909");
    }
  }

  @Test
  @DisplayName("Dado exceção com PII na mensagem / Quando loga / Então o stack_trace sai mascarado")
  void stackTraceMascarado() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      LOG.error(
          "falhou",
          new IllegalStateException(
              "cliente cpf=123.456.789-09 email=a.b@x.com Authorization: Bearer abc.def-ghi12345"));

      assertThat(logs.texto())
          .contains("IllegalStateException", "QaAppenderTest")
          .doesNotContain("123.456.789-09", "a.b@x.com", "abc.def-ghi12345");
    }
  }

  @Test
  @DisplayName("Dado PII num valor de MDC / Quando loga / Então sai mascarado e o traceId intacto")
  void mdcMascaradoETraceIdIntacto() {
    String trace = "12345678901234567890123456789012";
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      org.slf4j.MDC.put("traceId", trace);
      org.slf4j.MDC.put("operador", "email=zeca@x.com");
      LOG.info("com mdc");

      assertThat(logs.texto()).doesNotContain("zeca@x.com").contains(trace);
    } finally {
      org.slf4j.MDC.clear();
    }
  }

  @Test
  @DisplayName("Dado quebra de linha forjando evento / Quando loga / Então continua 1 evento só")
  void injecaoDeLinhaNaoForjaEvento() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      LOG.info("a\n{\"level\":\"ERROR\",\"message\":\"forjado\"}\r\nb");

      assertThat(logs.texto().lines()).hasSize(1);
      assertThat(logs.eventos()).singleElement();
      assertThat(logs.eventos("ERROR")).isEmpty();
    }
  }
}
