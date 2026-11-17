package com.recorra.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.common.trace.ContextoDeTrabalho;
import com.recorra.common.trace.MdcEscopo;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

@SuppressWarnings("try")
class TraceparentRestClientInterceptorTest {

  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

  private String enviar(MockClientHttpRequest request) throws IOException {
    new TraceparentRestClientInterceptor()
        .intercept(request, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], 200));
    return request.getHeaders().getFirst("traceparent");
  }

  @Test
  void propagaOTraceDaThreadComNovoSpan() throws IOException {
    try (MdcEscopo e = ContextoDeTrabalho.restaurar("A", TRACE)) {
      String header =
          enviar(new MockClientHttpRequest(HttpMethod.GET, URI.create("http://core/x")));

      assertThat(header).startsWith("00-" + TRACE + "-").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }
  }

  @Test
  void semTraceNaoInventaHeader() throws IOException {
    assertThat(enviar(new MockClientHttpRequest(HttpMethod.GET, URI.create("http://core/x"))))
        .isNull();
  }
}
