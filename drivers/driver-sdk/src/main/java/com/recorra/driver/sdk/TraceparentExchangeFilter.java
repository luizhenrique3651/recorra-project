package com.recorra.driver.sdk;

import com.recorra.common.trace.TraceContext;
import com.recorra.common.trace.Traceparent;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Saída HTTP com {@code WebClient}: acrescenta o {@code traceparent} do trabalho em curso (novo
 * span, mesmo trace). Lê o contexto capturado pelo {@link ResilientErpClient} no Reactor Context
 * (vale em TODA tentativa, inclusive as resubscritas em outra thread) e, na falta dele, o MDC da
 * thread que se inscreve. Para o cliente do core (ingestão, REC-043). NÃO use no {@link
 * ResilientErpClient}: o trace interno não deve ir para o ERP.
 */
public final class TraceparentExchangeFilter implements ExchangeFilterFunction {

  @Override
  public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
    return Mono.deferContextual(
        contexto -> {
          Map<String, String> foto = contexto.getOrDefault(TraceContext.REACTOR_KEY, null);
          Optional<String> header =
              foto != null
                  ? TraceContext.deFoto(foto).map(t -> t.filho().toHeader())
                  : TraceContext.proximoHeader();
          return next.exchange(
              header
                  .map(h -> ClientRequest.from(request).header(Traceparent.HEADER, h).build())
                  .orElse(request));
        });
  }
}
