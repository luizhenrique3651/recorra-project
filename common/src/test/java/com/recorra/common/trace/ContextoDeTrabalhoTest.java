package com.recorra.common.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@SuppressWarnings("try") // escopos existem só para fechar o MDC
class ContextoDeTrabalhoTest {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ContextoDeTrabalhoTest.class);
  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

  private final Logger logger = (Logger) LoggerFactory.getLogger(ContextoDeTrabalhoTest.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  @BeforeEach
  void captura() {
    MDC.clear();
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void solta() {
    logger.detachAppender(logs);
    MDC.clear();
  }

  private Map<String, String> mdcDa(int i) {
    return logs.list.get(i).getMDCPropertyMap();
  }

  @Test
  @DisplayName(
      "Dado um job com os comércios A e B / Quando roda / Então cada log só tem o seu comercioId e o MDC fica vazio")
  void jobPorComercioNaoVaza() {
    for (String comercio : List.of("A", "B")) {
      ContextoDeTrabalho.executar(comercio, () -> LOG.info("processando"));
    }

    assertThat(mdcDa(0)).containsEntry("comercioId", "A").containsKeys("traceId", "spanId");
    assertThat(mdcDa(1)).containsEntry("comercioId", "B").containsKeys("traceId", "spanId");
    assertThat(mdcDa(0).get("traceId"))
        .as("trace NOVO por unidade")
        .isNotEqualTo(mdcDa(1).get("traceId"));
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  @DisplayName("Dado uma exceção no trabalho / Quando propaga / Então o MDC é limpo mesmo assim")
  void limpaEmExcecao() {
    assertThatThrownBy(
            () ->
                ContextoDeTrabalho.executar(
                    "A",
                    () -> {
                      throw new IllegalStateException("falhou");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  @DisplayName(
      "Dado um consumer com traceparent do header/outbox / Quando processa / Então restaura o traceId e limpa ao final")
  void consumerRestauraOTraceId() {
    ContextoDeTrabalho.executar(
        "A", "00-" + TRACE + "-00f067aa0ba902b7-01", () -> LOG.info("consumindo"));
    ContextoDeTrabalho.executar("B", TRACE, () -> LOG.info("consumindo outbox (só traceId)"));

    assertThat(mdcDa(0)).containsEntry("traceId", TRACE).containsEntry("comercioId", "A");
    assertThat(mdcDa(1)).containsEntry("traceId", TRACE).containsEntry("comercioId", "B");
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  @DisplayName(
      "Dado header ausente ou inválido / Quando restaura / Então o log não fica sem traceId")
  void valorInvalidoComecaTraceNovo() {
    ContextoDeTrabalho.executar("A", "lixo", () -> LOG.info("x"));
    ContextoDeTrabalho.executar("A", null, () -> LOG.info("y"));

    assertThat(mdcDa(0).get("traceId")).hasSize(32);
    assertThat(mdcDa(1).get("traceId")).hasSize(32);
  }

  @Test
  @DisplayName("Dado escopos aninhados / Quando o interno fecha / Então o contexto de fora volta")
  void escoposAninhadosRestauram() {
    try (MdcEscopo fora = ContextoDeTrabalho.restaurar("A", TRACE)) {
      try (MdcEscopo dentro = ContextoDeTrabalho.restaurar("B", null)) {
        assertThat(MDC.get("comercioId")).isEqualTo("B");
      }
      assertThat(MDC.get("comercioId")).isEqualTo("A");
      assertThat(MDC.get("traceId")).isEqualTo(TRACE);
    }
    assertThat(MDC.get("comercioId")).isNull();
  }

  @Test
  void novoSpanMantemOTraceECriaTraceSeNaoHaNenhum() {
    try (MdcEscopo sem = TraceContext.novoSpan()) {
      assertThat(TraceContext.atual()).isPresent();
    }
    assertThat(TraceContext.atual()).isEmpty();

    try (MdcEscopo pai = ContextoDeTrabalho.restaurar("A", TRACE)) {
      String spanPai = MDC.get("spanId");
      try (MdcEscopo filho = TraceContext.novoSpan()) {
        assertThat(TraceContext.traceIdAtual()).isEqualTo(TRACE);
        assertThat(MDC.get("spanId")).isNotEqualTo(spanPai);
      }
      assertThat(MDC.get("spanId")).isEqualTo(spanPai);
    }
  }

  @Test
  @DisplayName(
      "Dado um trace não amostrado (flags 00) / Quando restaura e abre novo span / Então as flags são preservadas")
  void preservaAsFlagsRecebidas() {
    try (MdcEscopo e = ContextoDeTrabalho.restaurar("A", "00-" + TRACE + "-00f067aa0ba902b7-00")) {
      assertThat(TraceContext.atual().orElseThrow().flags()).isEqualTo("00");
      try (MdcEscopo filho = TraceContext.novoSpan()) {
        assertThat(TraceContext.atual().orElseThrow().toHeader()).endsWith("-00");
      }
    }
    assertThat(MDC.get("traceFlags")).isNull();
  }

  @Test
  void comercioObrigatorio() {
    assertThatThrownBy(() -> ContextoDeTrabalho.executar(null, () -> {}))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void atualIgnoraMdcCorrompido() {
    MDC.put("traceId", "invalido");
    MDC.put("spanId", "invalido");

    assertThat(TraceContext.atual()).isEmpty();
  }

  @Test
  @DisplayName(
      "Dado o contexto de uma thread / Quando captura e restaura em outra / Então vai o mesmo contexto e a thread volta limpa")
  void capturaERestauraEntreThreads() throws Exception {
    Map<String, String> foto;
    try (MdcEscopo e = ContextoDeTrabalho.restaurar("A", TRACE)) {
      foto = TraceContext.capturar();
    }
    assertThat(foto).containsEntry("traceId", TRACE).containsEntry("comercioId", "A");
    assertThat(TraceContext.deFoto(foto).orElseThrow().traceId()).isEqualTo(TRACE);
    assertThat(TraceContext.deFoto(Map.of())).isEmpty();
    assertThat(TraceContext.deFoto(Map.of("traceId", "x", "spanId", "y"))).isEmpty();

    String[] visto = new String[2];
    Thread t =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (MdcEscopo e = TraceContext.restaurar(foto)) {
                    visto[0] = MDC.get("traceId") + "/" + MDC.get("comercioId");
                  }
                  visto[1] = String.valueOf(MDC.get("traceId"));
                });
    t.join();

    assertThat(visto[0]).isEqualTo(TRACE + "/A");
    assertThat(visto[1]).isEqualTo("null");
  }

  @Test
  void proximoHeaderSoComTrace() {
    assertThat(TraceContext.proximoHeader()).isEmpty();
    try (MdcEscopo e = ContextoDeTrabalho.restaurar("A", TRACE)) {
      assertThat(TraceContext.proximoHeader().orElseThrow()).startsWith("00-" + TRACE + "-");
    }
  }
}
