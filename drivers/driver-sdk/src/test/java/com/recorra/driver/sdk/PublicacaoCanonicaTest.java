package com.recorra.driver.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicacaoCanonicaTest {

  /** Transporte de mentira: prova que o SDK não depende de transporte nenhum. */
  static class EmMemoria implements CanonicalPublisher {
    final List<IdempotencyKey> vistas = new ArrayList<>();

    @Override
    public PublishResult publish(Object evento, IdempotencyKey chave) {
      return vistas.contains(chave) ? PublishResult.DUPLICATE : record(chave);
    }

    private PublishResult record(IdempotencyKey chave) {
      vistas.add(chave);
      return PublishResult.ACCEPTED;
    }
  }

  @Test
  @DisplayName("Dado a mesma chave / Quando publico duas vezes / Então a 2ª é DUPLICATE")
  void mesmaChaveDuplicate() {
    EmMemoria publisher = new EmMemoria();
    CanonicalMessage m =
        new CanonicalMessage(new Object(), IdempotencyKey.paraVenda("c1", "erp", "1"));

    assertThat(publisher.publish(m)).isEqualTo(PublishResult.ACCEPTED);
    assertThat(publisher.publish(m)).isEqualTo(PublishResult.DUPLICATE);
  }

  @Test
  @DisplayName(
      "Dado um decorador / Quando publico / Então repassa ao delegado (ponto do traceparent)")
  void decoradorRepassa() {
    EmMemoria base = new EmMemoria();
    List<String> chamadas = new ArrayList<>();
    CanonicalPublisher decorado =
        new DelegatingCanonicalPublisher(base) {
          @Override
          public PublishResult publish(Object evento, IdempotencyKey chave) {
            chamadas.add("antes"); // aqui o REC-005 acrescentaria o traceparent
            return super.publish(evento, chave);
          }
        };

    assertThat(decorado.publish(new Object(), IdempotencyKey.paraVenda("c1", "e", "1")))
        .isEqualTo(PublishResult.ACCEPTED);
    assertThat(chamadas).containsExactly("antes");
    assertThat(base.vistas).hasSize(1);
  }

  @Test
  void decoradorExigeDelegado() {
    assertThatThrownBy(() -> new DelegatingCanonicalPublisher(null) {})
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Dado uma mensagem / Quando imprimo / Então não vaza o evento (PII)")
  void mensagemNaoVazaEventoNoToString() {
    CanonicalMessage m =
        new CanonicalMessage("CPF 123.456.789-00", IdempotencyKey.paraVenda("c1", "erp", "1"));

    assertThat(m.toString()).doesNotContain("123.456").contains("erp:1");
    assertThatThrownBy(() -> new CanonicalMessage(null, IdempotencyKey.paraVenda("c1", "e", "1")))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new CanonicalMessage(new Object(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
