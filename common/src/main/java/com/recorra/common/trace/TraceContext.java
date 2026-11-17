package com.recorra.common.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;

/** Contexto de rastreio da thread atual, guardado no MDC (as chaves saem em todo log JSON). */
public final class TraceContext {

  public static final String TRACE_ID = "traceId";
  public static final String SPAN_ID = "spanId";

  /** Flags do W3C (ex.: 00 = não amostrado); preservadas de ponta a ponta. */
  public static final String TRACE_FLAGS = "traceFlags";

  public static final String COMERCIO_ID = "comercioId";

  /** Chave do snapshot no Reactor Context (o SDK é lib: nada de Hook global). */
  public static final String REACTOR_KEY = "recorra.contexto-de-trabalho";

  private static final String[] CHAVES = {TRACE_ID, SPAN_ID, TRACE_FLAGS, COMERCIO_ID};

  private TraceContext() {}

  /** O trace da thread atual, se houver. */
  public static Optional<Traceparent> atual() {
    String traceId = MDC.get(TRACE_ID);
    String spanId = MDC.get(SPAN_ID);
    if (traceId == null || spanId == null) {
      return Optional.empty();
    }
    try {
      String flags = MDC.get(TRACE_FLAGS);
      return Optional.of(new Traceparent(traceId, spanId, flags == null ? "01" : flags));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Foto do contexto da thread atual (traceId, spanId, flags, comercioId): imutável, para levar a
   * outras threads (Reactor, executores), onde o MDC não vai sozinho.
   */
  public static Map<String, String> capturar() {
    Map<String, String> foto = new LinkedHashMap<>();
    for (String chave : CHAVES) {
      String valor = MDC.get(chave);
      if (valor != null) {
        foto.put(chave, valor);
      }
    }
    return Map.copyOf(foto);
  }

  /** Reabre uma foto na thread atual; fechar restaura o MDC anterior. */
  public static MdcEscopo restaurar(Map<String, String> foto) {
    return new MdcEscopo(new LinkedHashMap<>(foto));
  }

  /** O trace de uma foto (sem tocar no MDC). */
  public static Optional<Traceparent> deFoto(Map<String, String> foto) {
    String traceId = foto.get(TRACE_ID);
    String spanId = foto.get(SPAN_ID);
    if (traceId == null || spanId == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new Traceparent(traceId, spanId, foto.getOrDefault(TRACE_FLAGS, "01")));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Header {@code traceparent} da próxima chamada de saída (novo span), se houver trace. */
  public static Optional<String> proximoHeader() {
    return atual().map(t -> t.filho().toHeader());
  }

  public static String traceIdAtual() {
    return MDC.get(TRACE_ID);
  }

  /** Abre um escopo com este trace (e, se houver, o comércio já corrente permanece). */
  public static MdcEscopo abrir(Traceparent trace) {
    Map<String, String> valores = new LinkedHashMap<>();
    valores.put(TRACE_ID, trace.traceId());
    valores.put(SPAN_ID, trace.spanId());
    valores.put(TRACE_FLAGS, trace.flags());
    return new MdcEscopo(valores);
  }

  /**
   * Um novo span: filho do trace atual ou, sem trace, um trace novo. É o que o decorador do {@code
   * CanonicalPublisher} usa para o transporte ler o {@code traceparent} correto.
   */
  public static MdcEscopo novoSpan() {
    return abrir(atual().map(Traceparent::filho).orElseGet(Traceparent::novo));
  }
}
