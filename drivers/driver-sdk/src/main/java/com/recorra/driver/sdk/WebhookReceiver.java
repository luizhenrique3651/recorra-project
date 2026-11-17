package com.recorra.driver.sdk;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Esqueleto do receptor de webhook do ERP: valida a assinatura, traduz o corpo para eventos
 * canônicos e os publica. O endpoint HTTP, a assinatura e a tradução são do driver; o SDK só fixa a
 * ordem (nunca traduzir ou publicar sem assinatura válida).
 *
 * <p><b>Provisório:</b> este esqueleto é síncrono. No REC-043 o endpoint responde {@code 202} só
 * depois de gravar o evento no outbox do driver e o processamento passa a ser assíncrono; o
 * esqueleto deve ser adaptado a isso.
 *
 * <p>Logs: assinatura inválida vira WARN só com a classe do receptor; nunca cabeçalhos nem corpo.
 */
public abstract class WebhookReceiver {

  private static final Logger LOG = LoggerFactory.getLogger(WebhookReceiver.class);

  private final CanonicalPublisher publisher;

  protected WebhookReceiver(CanonicalPublisher publisher) {
    this.publisher = Objects.requireNonNull(publisher, "publisher");
  }

  /** O corpo veio mesmo do ERP? (HMAC, token...). */
  protected abstract boolean assinaturaValida(Map<String, List<String>> cabecalhos, byte[] corpo);

  /** Traduz o corpo do webhook para eventos canônicos (pode ser vazia). */
  protected abstract List<CanonicalMessage> traduzir(byte[] corpo);

  public final WebhookOutcome receive(Map<String, List<String>> cabecalhos, byte[] corpo) {
    if (!assinaturaValida(cabecalhos, corpo)) {
      LOG.warn("webhook rejeitado: assinatura inválida (receptor {})", getClass().getName());
      return WebhookOutcome.REJECTED;
    }
    traduzir(corpo).forEach(publisher::publish);
    return WebhookOutcome.ACCEPTED;
  }
}
