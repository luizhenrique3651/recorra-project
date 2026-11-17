package com.recorra.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.recorra.common.logging.CapturaDeLogJson;
import com.recorra.common.teste.AppDeTeste;
import com.recorra.common.trace.MdcEscopo;
import com.recorra.common.trace.TraceContext;
import com.recorra.common.trace.Traceparent;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SuppressWarnings("try")
@SpringBootTest(classes = AppDeTeste.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ObservabilidadeWebTest {

  private static final int UNPROCESSABLE = 422;

  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE + "-00f067aa0ba902b7-01";

  @Autowired RestTestClient client;

  /** O log de request sai no finally do filtro, depois da resposta: espera ele aparecer. */
  private static void aguardarLogDeRequest(CapturaDeLogJson logs) {
    await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(() -> assertThat(logs.texto()).contains("request "));
  }

  @Test
  @DisplayName(
      "Dado uma requisição com traceparent que falha / Quando processada / Então o ProblemDetail e o log têm o mesmo traceId")
  void traceparentDeEntradaChegaAoProblemDetailEAosLogs() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/teste/falha")
          .header("traceparent", TRACEPARENT)
          .exchange()
          .expectStatus()
          .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
          .expectHeader()
          .value("traceparent", h -> assertThat(h).startsWith("00-" + TRACE + "-"))
          .expectBody()
          .jsonPath("$.traceId")
          .isEqualTo(TRACE);

      assertThat(logs.eventos())
          .isNotEmpty()
          .allSatisfy(e -> assertThat(e).containsEntry("traceId", TRACE));
    }
  }

  @Test
  @DisplayName(
      "Dado uma requisição sem traceparent / Quando processada / Então gera um trace e o devolve")
  void semHeaderGeraTrace() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      var resposta =
          client.get().uri("/teste/ok").exchange().expectStatus().isOk().returnResult(String.class);
      String header = resposta.getResponseHeaders().getFirst("traceparent");

      assertThat(header).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
      String traceId = header.split("-")[1];
      assertThat(logs.eventos())
          .isNotEmpty()
          .allSatisfy(e -> assertThat(e).containsEntry("traceId", traceId));
    }
  }

  @Test
  @DisplayName("Dado um traceparent malformado / Quando processada / Então começa um trace novo")
  void headerMalformadoComecaTraceNovo() {
    var resposta =
        client
            .get()
            .uri("/teste/ok")
            .header("traceparent", "lixo")
            .exchange()
            .returnResult(String.class);

    assertThat(resposta.getResponseHeaders().getFirst("traceparent"))
        .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
  }

  @Test
  @DisplayName(
      "Dado uma rejeição de negócio / Então WARN sem stacktrace e ProblemDetail 422 com traceId")
  void rejeicaoDeNegocioEWarnSemStacktrace() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/teste/negocio")
          .header("traceparent", TRACEPARENT)
          .exchange()
          .expectStatus()
          .isEqualTo(422)
          .expectBody()
          .jsonPath("$.traceId")
          .isEqualTo(TRACE)
          .jsonPath("$.codigo")
          .isEqualTo("JANELA_INVALIDA")
          .jsonPath("$.title")
          .isEqualTo("Regra de negócio violada");

      assertThat(logs.eventos("WARN"))
          .singleElement()
          .satisfies(
              e -> {
                assertThat((String) e.get("message")).contains("JANELA_INVALIDA", "422");
                assertThat(e).doesNotContainKey("stack_trace");
              });
      assertThat(logs.eventos("ERROR")).isEmpty();
      assertThat(logs.texto())
          .as("detalhe da regra não vai para o log")
          .doesNotContain("maior que zero");
    }
  }

  @Test
  @DisplayName("Dado uma exceção inesperada / Então um único ERROR com stacktrace e 500 genérico")
  void inesperadoEUmUnicoErrorComStacktrace() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/teste/falha")
          .exchange()
          .expectStatus()
          .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
          .expectBody(String.class)
          .value(
              corpo ->
                  assertThat(corpo)
                      .contains("Erro interno", "traceId")
                      .doesNotContain("detalhe interno")
                      .doesNotContain("jdbc")
                      .doesNotContain("IllegalStateException"));

      assertThat(logs.eventos("ERROR"))
          .singleElement()
          .satisfies(
              e -> assertThat((String) e.get("stack_trace")).contains("IllegalStateException"));
      assertThat(logs.eventos("WARN")).isEmpty();
    }
  }

  @Test
  @DisplayName(
      "Dado erro de requisição do Spring (404) / Então WARN sem stacktrace e ProblemDetail com traceId")
  void erroDeRequisicaoDoSpringEWarn() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/nao/existe")
          .header("traceparent", TRACEPARENT)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.traceId")
          .isEqualTo(TRACE);

      assertThat(logs.eventos("WARN"))
          .singleElement()
          .satisfies(e -> assertThat(e).doesNotContainKey("stack_trace"));
      assertThat(logs.eventos("ERROR")).isEmpty();
    }
  }

  @Test
  @DisplayName("Dado um request com body / Quando logado / Então o body não aparece em log")
  void bodyNuncaApareceNoLog() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .post()
          .uri("/teste/corpo")
          .contentType(MediaType.TEXT_PLAIN)
          .body("conteudo-secreto-do-webhook-xyz")
          .exchange()
          .expectStatus()
          .isOk();

      aguardarLogDeRequest(logs);
      assertThat(logs.texto()).doesNotContain("conteudo-secreto");
    }
  }

  @Test
  @DisplayName(
      "Dado GET /callback?code=abc&state=xyz / Quando logado / Então code e state saem mascarados")
  void queryOauthMascarada() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/driver/v1/bling/callback?code=abc123&state=xyz789&token=tok456&pagina=2")
          .exchange()
          .expectStatus()
          .isOk();

      aguardarLogDeRequest(logs);
      assertThat(logs.eventos())
          .anySatisfy(
              e ->
                  assertThat((String) e.get("message"))
                      .contains("/driver/v1/bling/callback?code=***&state=***&token=***&pagina=2"));
      assertThat(logs.texto()).doesNotContain("abc123", "xyz789", "tok456");
    }
  }

  @Test
  @DisplayName(
      "Dado /clientes/{cpf} / Quando logado / Então a rota sai pelo padrão, sem o identificador")
  void rotaSaiPeloPadrao() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client.get().uri("/clientes/12345678909").exchange().expectStatus().isOk();
      aguardarLogDeRequest(logs);

      assertThat(logs.eventos())
          .anySatisfy(e -> assertThat((String) e.get("message")).contains("GET /clientes/{id}"));
      assertThat(logs.texto()).doesNotContain("12345678909");
    }
  }

  @Test
  @DisplayName(
      "Dado um log com telefone, nome, CPF, e-mail e token / Quando o appender escreve / Então todos saem mascarados")
  void appenderMascaraPii() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client.get().uri("/teste/pii").exchange().expectStatus().isOk();

      String texto = logs.texto();
      assertThat(texto)
          .doesNotContain(
              "João", "Silva", "98765", "4321", "123.456.789", "joao.silva", "example.com")
          .doesNotContain("tok-super-secreto");
      Map<String, Object> linha =
          logs.eventos().stream()
              .filter(e -> ((String) e.get("message")).startsWith("cliente"))
              .findFirst()
              .orElseThrow();
      assertThat((String) linha.get("message"))
          .contains("nome=***", "telefone=***", "access_token=***");
    }
  }

  @Test
  @DisplayName("Dado actuator / Quando chamado / Então não gera log de request")
  void actuatorFicaForaDoLogDeRequest() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client.get().uri("/actuator/health").exchange();
      client.get().uri("/teste/ok").exchange();
      aguardarLogDeRequest(logs); // o do /teste/ok chegou: o do actuator, se existisse, também

      assertThat(logs.texto()).doesNotContain("request GET /actuator");
    }
  }

  @Test
  @DisplayName(
      "Dado PII codificada na query / Quando logado / Então nome, telefone e e-mail não aparecem")
  void queryComPiiCodificadaNaoVaza() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/teste/ok?nome=Maria%20Silva&telefone=1133334444&email=a%40b.com&pagina=2")
          .exchange()
          .expectStatus()
          .isOk();

      aguardarLogDeRequest(logs);
      assertThat(logs.texto())
          .doesNotContain("Maria", "Silva", "1133334444", "a%40b.com", "a@b.com")
          .contains("nome=***", "telefone=***", "email=***", "pagina=2");
    }
  }

  @Test
  @DisplayName(
      "Dado um traceparent não amostrado / Quando processada / Então a resposta preserva as flags 00")
  void flagsPreservadasNaResposta() {
    var resposta =
        client
            .get()
            .uri("/teste/ok")
            .header("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-00")
            .exchange()
            .returnResult(String.class);

    assertThat(resposta.getResponseHeaders().getFirst("traceparent")).endsWith("-00");
  }

  @Test
  @DisplayName(
      "Dado um filtro que lança / Quando processada / Então 500 ProblemDetail com o traceId, exatamente 1 ERROR e request log com status 500")
  void excecaoForaDoMvcViraUmErrorEResposta500() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      client
          .get()
          .uri("/teste/filtro-lanca?pagina=1")
          .header("traceparent", TRACEPARENT)
          .exchange()
          .expectStatus()
          .isEqualTo(500)
          .expectHeader()
          .contentTypeCompatibleWith("application/problem+json")
          .expectBody(String.class)
          .value(
              corpo ->
                  assertThat(corpo)
                      .contains("\"traceId\":\"" + TRACE + "\"", "Erro interno")
                      .doesNotContain("jdbc")
                      .doesNotContain("IllegalStateException"));

      aguardarLogDeRequest(logs);
      assertThat(logs.eventos("ERROR"))
          .singleElement()
          .satisfies(
              e -> {
                assertThat(e).containsEntry("traceId", TRACE);
                assertThat((String) e.get("stack_trace")).contains("IllegalStateException");
              });
      assertThat(logs.eventos("INFO"))
          .anySatisfy(
              e ->
                  assertThat((String) e.get("message"))
                      .contains("status=500", "erro=IllegalStateException", "pagina=1"));
    }
  }

  @Test
  @DisplayName(
      "Dado um traceId com 11 dígitos seguidos / Quando o appender escreve / Então o traceId sai inteiro no JSON")
  void traceIdComDigitosSaiIntacto() {
    String traceId = "aaaaaaaa12345678901bbbbbbbbbbbbb";
    try (CapturaDeLogJson logs = new CapturaDeLogJson();
        MdcEscopo escopo = TraceContext.abrir(new Traceparent(traceId, "00f067aa0ba902b7", "01"))) {
      org.slf4j.LoggerFactory.getLogger(ObservabilidadeWebTest.class).info("com trace");

      assertThat(logs.eventos())
          .singleElement()
          .satisfies(e -> assertThat(e).containsEntry("traceId", traceId));
    }
  }

  @Test
  @DisplayName(
      "Dado um erro descartado pelo Reactor depois de cancelamento / Quando ocorre / Então não vira ERROR (o hook vem da autoconfiguração)")
  void erroDescartadoNaoViraError() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      reactor.core.publisher.Operators.onErrorDropped(
          new java.util.concurrent.CancellationException("Request cancelled"),
          reactor.util.context.Context.empty());
      reactor.core.publisher.Operators.onErrorDropped(
          new IllegalStateException("detalhe interno"), reactor.util.context.Context.empty());

      assertThat(logs.eventos("ERROR")).isEmpty();
      assertThat(logs.eventos("WARN"))
          .singleElement()
          .satisfies(
              e -> {
                assertThat((String) e.get("message")).contains("IllegalStateException");
                assertThat(e).doesNotContainKey("stack_trace");
              });
      assertThat(logs.texto()).doesNotContain("detalhe interno");
    }
  }

  @Test
  @DisplayName(
      "Dado um log com marker (campo em array) / Quando o appender escreve / Então o evento não se perde e o elemento do array é mascarado")
  void elementoDeArrayPassaPeloMascaramento() {
    try (CapturaDeLogJson logs = new CapturaDeLogJson()) {
      org.slf4j.LoggerFactory.getLogger(ObservabilidadeWebTest.class)
          .info(org.slf4j.MarkerFactory.getMarker("dono=maria@example.com"), "com marker");

      assertThat(logs.eventos()).singleElement();
      assertThat(logs.texto()).contains("com marker").doesNotContain("maria@example.com");
    }
  }
}
