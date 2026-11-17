package com.recorra.driver.sdk;

import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Esqueleto do polling incremental: lê do ERP a partir do último cursor, publica cada evento e só
 * então avança o cursor. Entrega <i>at-least-once</i>: se cair no meio, a próxima rodada repete
 * eventos e a {@link IdempotencyKey} os descarta no core. O agendamento (quando chamar {@link
 * #pollOnce()}) e a leitura do ERP são do driver; o SDK não conhece ERP nenhum.
 */
public abstract class IncrementalPoller {

  /** Uma página lida do ERP: eventos já traduzidos para o canônico e o cursor seguinte. */
  public record Page(List<CanonicalMessage> mensagens, String proximoCursor) {
    public Page {
      mensagens = List.copyOf(mensagens);
      Objects.requireNonNull(proximoCursor, "proximoCursor");
    }
  }

  private static final Logger LOG = LoggerFactory.getLogger(IncrementalPoller.class);

  private final CursorStore cursores;
  private final CanonicalPublisher publisher;

  protected IncrementalPoller(CursorStore cursores, CanonicalPublisher publisher) {
    this.cursores = Objects.requireNonNull(cursores, "cursores");
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  /** Lê do ERP tudo que mudou depois de {@code cursor} (nulo na primeira rodada). */
  protected abstract Page fetchSince(String cursor);

  /**
   * Uma rodada: lê, publica e avança o cursor.
   *
   * @return quantos eventos foram publicados
   */
  public final int pollOnce() {
    Page pagina = fetchSince(cursores.load().orElse(null));
    List<CanonicalMessage> mensagens = pagina.mensagens();
    for (int i = 0; i < mensagens.size(); i++) {
      try {
        publisher.publish(mensagens.get(i));
      } catch (RuntimeException e) {
        // Só a chave e a posição: nada da mensagem do erro nem do evento (PII).
        LOG.warn(
            "falha ao publicar chave={} (posição {}/{} da página); cursor não avança",
            mensagens.get(i).chave(),
            i + 1,
            mensagens.size());
        throw e;
      }
    }
    cursores.save(pagina.proximoCursor());
    LOG.debug("rodada concluída: {} evento(s) publicado(s)", mensagens.size());
    return mensagens.size();
  }
}
