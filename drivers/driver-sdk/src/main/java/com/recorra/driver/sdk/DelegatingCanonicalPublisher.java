package com.recorra.driver.sdk;

import java.util.Objects;

/**
 * Base de decoradores do {@link CanonicalPublisher} (ponto de extensão): repassa tudo ao delegado.
 * O REC-005 estende esta classe para propagar o {@code traceparent}; métricas e auditoria também
 * podem usá-la.
 */
public abstract class DelegatingCanonicalPublisher implements CanonicalPublisher {

  private final CanonicalPublisher delegado;

  protected DelegatingCanonicalPublisher(CanonicalPublisher delegado) {
    this.delegado = Objects.requireNonNull(delegado, "delegado");
  }

  @Override
  public PublishResult publish(Object evento, IdempotencyKey chave) {
    return delegado.publish(evento, chave);
  }
}
