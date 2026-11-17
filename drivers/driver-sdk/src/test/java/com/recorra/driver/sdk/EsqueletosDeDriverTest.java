package com.recorra.driver.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EsqueletosDeDriverTest {

  private final List<IdempotencyKey> publicadas = new ArrayList<>();
  private final CanonicalPublisher publisher =
      (evento, chave) -> {
        publicadas.add(chave);
        return PublishResult.ACCEPTED;
      };

  private static CanonicalMessage msg(String id) {
    return new CanonicalMessage("evento-" + id, IdempotencyKey.paraVenda("c1", "erp", id));
  }

  static class CursorEmMemoria implements CursorStore {
    String valor;

    @Override
    public Optional<String> load() {
      return Optional.ofNullable(valor);
    }

    @Override
    public void save(String cursor) {
      valor = cursor;
    }
  }

  @Test
  @DisplayName("Dado o poller / Quando roda duas vezes / Então lê a partir do cursor salvo")
  void pollerAvancaOCursor() {
    CursorEmMemoria cursores = new CursorEmMemoria();
    List<String> pedidos = new ArrayList<>();
    IncrementalPoller poller =
        new IncrementalPoller(cursores, publisher) {
          @Override
          protected Page fetchSince(String cursor) {
            pedidos.add(String.valueOf(cursor));
            return cursor == null
                ? new Page(List.of(msg("1"), msg("2")), "c2")
                : new Page(List.of(), cursor);
          }
        };

    assertThat(poller.pollOnce()).isEqualTo(2);
    assertThat(poller.pollOnce()).isZero();

    assertThat(pedidos).containsExactly("null", "c2");
    assertThat(cursores.valor).isEqualTo("c2");
    assertThat(publicadas).hasSize(2);
  }

  @Test
  @DisplayName("Dado falha ao publicar / Quando o poller roda / Então o cursor NÃO avança")
  void falhaNaoAvancaOCursor() {
    CursorEmMemoria cursores = new CursorEmMemoria();
    IncrementalPoller poller =
        new IncrementalPoller(
            cursores,
            (evento, chave) -> {
              throw new IllegalStateException("core fora");
            }) {
          @Override
          protected Page fetchSince(String cursor) {
            return new Page(List.of(msg("1")), "c1");
          }
        };

    assertThatThrownBy(poller::pollOnce).isInstanceOf(IllegalStateException.class);
    assertThat(cursores.valor).isNull();
  }

  @Test
  void paginaExigeCursorEIsolaALista() {
    List<CanonicalMessage> origem = new ArrayList<>(List.of(msg("1")));
    IncrementalPoller.Page pagina = new IncrementalPoller.Page(origem, "c");
    origem.clear();

    assertThat(pagina.mensagens()).hasSize(1);
    assertThatThrownBy(() -> new IncrementalPoller.Page(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  private WebhookReceiver receiver(boolean assinaturaOk, List<String> traduzidos) {
    return new WebhookReceiver(publisher) {
      @Override
      protected boolean assinaturaValida(Map<String, List<String>> cabecalhos, byte[] corpo) {
        return assinaturaOk;
      }

      @Override
      protected List<CanonicalMessage> traduzir(byte[] corpo) {
        traduzidos.add(new String(corpo));
        return List.of(msg("9"));
      }
    };
  }

  @Test
  @DisplayName(
      "Dado assinatura inválida / Quando chega o webhook / Então rejeita sem traduzir nem publicar")
  void webhookSemAssinaturaERejeitado() {
    List<String> traduzidos = new ArrayList<>();

    WebhookOutcome r = receiver(false, traduzidos).receive(Map.of(), "x".getBytes());

    assertThat(r).isEqualTo(WebhookOutcome.REJECTED);
    assertThat(traduzidos).isEmpty();
    assertThat(publicadas).isEmpty();
  }

  @Test
  @DisplayName("Dado assinatura válida / Quando chega o webhook / Então traduz e publica")
  void webhookValidoPublica() {
    List<String> traduzidos = new ArrayList<>();

    WebhookOutcome r = receiver(true, traduzidos).receive(Map.of(), "corpo".getBytes());

    assertThat(r).isEqualTo(WebhookOutcome.ACCEPTED);
    assertThat(traduzidos).containsExactly("corpo");
    assertThat(publicadas).containsExactly(IdempotencyKey.paraVenda("c1", "erp", "9"));
  }
}
