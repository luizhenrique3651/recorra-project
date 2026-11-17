package com.recorra.driver.sdk;

import com.recorra.common.trace.MdcEscopo;
import com.recorra.common.trace.TraceContext;

/**
 * Decorador do {@link CanonicalPublisher} para o {@code traceparent} (REC-005): cada publicação
 * roda num novo span do trace corrente (ou num trace novo, se a thread não tem um), e restaura o
 * contexto ao final. O transporte plugável lê {@link TraceContext#atual()} e envia o header (HTTP
 * de ingestão, REC-043) ou grava o {@code traceId} no outbox (REC-042); o SDK continua sem
 * transporte.
 */
public final class TracingCanonicalPublisher extends DelegatingCanonicalPublisher {

  public TracingCanonicalPublisher(CanonicalPublisher delegado) {
    super(delegado);
  }

  @Override
  @SuppressWarnings("try") // o escopo só existe para restaurar o MDC em finally
  public PublishResult publish(Object evento, IdempotencyKey chave) {
    try (MdcEscopo span = TraceContext.novoSpan()) {
      return super.publish(evento, chave);
    }
  }
}
