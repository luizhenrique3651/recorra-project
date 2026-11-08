package com.recorra.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CoreApiApplicationSmokeTest {

  @Test
  void contextoSobe() {
    // Falha se o contexto Spring não subir.
  }

  @Test
  void healthRespondeUp(@Value("${local.server.port}") int port) throws Exception {
    URI uri = URI.create("http://localhost:" + port + "/actuator/health");
    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

    HttpResponse<String> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response = client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }
}
