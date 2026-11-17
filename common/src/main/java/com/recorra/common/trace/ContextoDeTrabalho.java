package com.recorra.common.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MDC por unidade de trabalho: todo job agendado, consumer de evento e handler de webhook abre um
 * escopo <b>para cada comércio processado</b> com {@code comercioId} + {@code traceId}, e o fecha
 * em {@code finally} (inclusive em exceção). Um comércio nunca aparece no log de outro.
 *
 * <pre>{@code
 * for (Comercio c : comercios) {
 *   ContextoDeTrabalho.executar(c.id(), () -> processar(c));            // job: trace NOVO
 * }
 * ContextoDeTrabalho.executar(comercioId, traceparentDoOutbox, () -> consumir(evento)); // consumer
 * }</pre>
 */
public final class ContextoDeTrabalho {

  private ContextoDeTrabalho() {}

  /** Job agendado: abre {@code comercioId} e um trace novo. */
  public static MdcEscopo abrirNovo(String comercioId) {
    return abrir(comercioId, Traceparent.novo());
  }

  /**
   * Consumer/webhook: abre {@code comercioId} e RESTAURA o trace do header ou do outbox (o {@code
   * traceparent} completo ou só o {@code traceId}). Valor ausente ou inválido começa um trace novo
   * (o log nunca fica sem {@code traceId}).
   */
  public static MdcEscopo restaurar(String comercioId, String traceparentOuTraceId) {
    return abrir(
        comercioId, Traceparent.restaurar(traceparentOuTraceId).orElseGet(Traceparent::novo));
  }

  @SuppressWarnings("try") // o escopo só existe para fechar (restaurar o MDC) em finally
  public static void executar(String comercioId, Runnable trabalho) {
    try (MdcEscopo escopo = abrirNovo(comercioId)) {
      trabalho.run();
    }
  }

  @SuppressWarnings("try")
  public static void executar(String comercioId, String traceparentOuTraceId, Runnable trabalho) {
    try (MdcEscopo escopo = restaurar(comercioId, traceparentOuTraceId)) {
      trabalho.run();
    }
  }

  private static MdcEscopo abrir(String comercioId, Traceparent trace) {
    Objects.requireNonNull(comercioId, "comercioId");
    Map<String, String> valores = new LinkedHashMap<>();
    valores.put(TraceContext.TRACE_ID, trace.traceId());
    valores.put(TraceContext.SPAN_ID, trace.spanId());
    valores.put(TraceContext.TRACE_FLAGS, trace.flags());
    valores.put(TraceContext.COMERCIO_ID, comercioId);
    return new MdcEscopo(valores);
  }
}
