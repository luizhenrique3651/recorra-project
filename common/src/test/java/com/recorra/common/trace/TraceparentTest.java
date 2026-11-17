package com.recorra.common.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TraceparentTest {

  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN = "00f067aa0ba902b7";
  private static final String VALIDO = "00-" + TRACE + "-" + SPAN + "-01";

  @Test
  @DisplayName("Dado um traceparent W3C válido / Quando leio / Então extrai trace, span e flags")
  void leHeaderValido() {
    Traceparent t = Traceparent.parse(VALIDO).orElseThrow();

    assertThat(t.traceId()).isEqualTo(TRACE);
    assertThat(t.spanId()).isEqualTo(SPAN);
    assertThat(t.flags()).isEqualTo("01");
    assertThat(t.toHeader()).isEqualTo(VALIDO);
  }

  @Test
  @DisplayName("Dado headers malformados / Quando leio / Então vazio")
  void rejeitaMalformados() {
    String[] ruins = {
      null,
      "",
      "lixo",
      "00-" + TRACE + "-" + SPAN,
      "ff-" + TRACE + "-" + SPAN + "-01",
      "00-" + "0".repeat(32) + "-" + SPAN + "-01",
      "00-" + TRACE + "-" + "0".repeat(16) + "-01",
      "00-" + TRACE.toUpperCase() + "-" + SPAN + "-01",
      "00-" + TRACE + "-" + SPAN + "-1",
      "00-" + TRACE + "-" + SPAN + "-01-extra",
      "0-" + TRACE + "-" + SPAN + "-01",
      "zz-" + TRACE + "-" + SPAN + "-01",
      "0g-" + TRACE + "-" + SPAN + "-01"
    };
    for (String ruim : ruins) {
      assertThat(Traceparent.parse(ruim)).as(String.valueOf(ruim)).isEmpty();
    }
  }

  @Test
  void versaoFuturaAceitaCamposExtras() {
    assertThat(Traceparent.parse("01-" + TRACE + "-" + SPAN + "-01-extra")).isPresent();
  }

  @Test
  @DisplayName("Dado um trace / Quando gero o filho / Então mantém o traceId e troca o span")
  void filhoMantemOTrace() {
    Traceparent pai = Traceparent.parse(VALIDO).orElseThrow();

    Traceparent filho = pai.filho();

    assertThat(filho.traceId()).isEqualTo(pai.traceId());
    assertThat(filho.spanId()).isNotEqualTo(pai.spanId()).hasSize(16);
    assertThat(filho.flags()).isEqualTo(pai.flags());
  }

  @Test
  void novoGeraIdsValidosEDistintos() {
    Traceparent a = Traceparent.novo();
    Traceparent b = Traceparent.novo();

    assertThat(Traceparent.parse(a.toHeader())).contains(a);
    assertThat(a.traceId()).isNotEqualTo(b.traceId());
  }

  @Test
  @DisplayName("Dado o traceId puro do outbox / Quando restauro / Então mantém o traceId")
  void restauraTraceIdPuroOuHeaderCompleto() {
    assertThat(Traceparent.restaurar(TRACE).orElseThrow().traceId()).isEqualTo(TRACE);
    assertThat(Traceparent.restaurar(VALIDO).orElseThrow().spanId()).isEqualTo(SPAN);
    assertThat(Traceparent.restaurar("  " + TRACE + " ")).isPresent();
    assertThat(Traceparent.restaurar("lixo")).isEmpty();
    assertThat(Traceparent.restaurar(null)).isEmpty();
    assertThat(Traceparent.restaurar("0".repeat(32))).isEmpty();
  }

  @Test
  void construtorValida() {
    assertThatThrownBy(() -> new Traceparent("x", SPAN, "01"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Traceparent(TRACE, SPAN, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
