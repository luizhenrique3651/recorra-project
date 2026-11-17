package com.recorra.driver.sdk;

import com.recorra.common.trace.MdcEscopo;
import com.recorra.common.trace.TraceContext;
import java.util.Map;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Leva o MDC do chamador para as threads do Reactor, sem Hook global (o SDK é lib). A inscrição e
 * cada sinal ({@code onNext/onError/onComplete}) que atravessa este operador rodam com o contexto
 * capturado, então tudo o que está a jusante nesse mesmo fluxo (retry, circuit breaker, rate
 * limiter e os seus logs) enxerga {@code traceId}/{@code comercioId} do chamador, mesmo na
 * resubscrição do retry numa thread do timer. O MDC anterior da thread é restaurado ao sair.
 */
final class MdcMono<T> extends Mono<T> {

  private final Mono<T> origem;
  private final Map<String, String> foto;

  MdcMono(Mono<T> origem, Map<String, String> foto) {
    this.origem = origem;
    this.foto = foto;
  }

  @Override
  @SuppressWarnings("try") // o escopo só existe para restaurar o MDC ao sair
  public void subscribe(CoreSubscriber<? super T> actual) {
    try (MdcEscopo escopo = TraceContext.restaurar(foto)) {
      origem.subscribe(new Assinante<>(actual, foto));
    }
  }

  private static final class Assinante<T> implements CoreSubscriber<T> {
    private final CoreSubscriber<? super T> jusante;
    private final Map<String, String> foto;

    Assinante(CoreSubscriber<? super T> jusante, Map<String, String> foto) {
      this.jusante = jusante;
      this.foto = foto;
    }

    @Override
    public Context currentContext() {
      return jusante.currentContext();
    }

    @Override
    @SuppressWarnings("try")
    public void onSubscribe(Subscription s) {
      try (MdcEscopo escopo = TraceContext.restaurar(foto)) {
        jusante.onSubscribe(s);
      }
    }

    @Override
    @SuppressWarnings("try")
    public void onNext(T valor) {
      try (MdcEscopo escopo = TraceContext.restaurar(foto)) {
        jusante.onNext(valor);
      }
    }

    @Override
    @SuppressWarnings("try")
    public void onError(Throwable erro) {
      try (MdcEscopo escopo = TraceContext.restaurar(foto)) {
        jusante.onError(erro);
      }
    }

    @Override
    @SuppressWarnings("try")
    public void onComplete() {
      try (MdcEscopo escopo = TraceContext.restaurar(foto)) {
        jusante.onComplete();
      }
    }
  }
}
