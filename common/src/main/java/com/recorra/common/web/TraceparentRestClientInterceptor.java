package com.recorra.common.web;

import com.recorra.common.trace.TraceContext;
import com.recorra.common.trace.Traceparent;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Saída HTTP ({@code RestClient}/{@code RestTemplate}): acrescenta o {@code traceparent} da thread
 * atual (novo span, mesmo trace). Use só para chamadas a serviços NOSSOS; não envie o trace interno
 * a terceiros (ERP, Meta).
 */
public class TraceparentRestClientInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    TraceContext.proximoHeader().ifPresent(h -> request.getHeaders().set(Traceparent.HEADER, h));
    return execution.execute(request, body);
  }
}
