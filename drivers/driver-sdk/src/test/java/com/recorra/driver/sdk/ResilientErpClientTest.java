package com.recorra.driver.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * WireMock: o ERP de mentira. Os tempos são sempre limites INFERIORES (robustos em máquina lenta).
 */
class ResilientErpClientTest {

  @RegisterExtension
  static WireMockExtension erp =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.options().dynamicPort())
          .build();

  private static final String PATH = "/vendas/1";

  @BeforeEach
  void limpaOErp() {
    erp.resetAll(); // stubs, cenários e contagem de requisições são por teste
  }

  private ResilientErpClient cliente(ErpClientConfig config) {
    WebClient web =
        WebClient.builder()
            .baseUrl(erp.baseUrl())
            .clientConnector(new JdkClientHttpConnector())
            .build();
    return new ResilientErpClient("erp-teste", web, config);
  }

  /** 3 tentativas, backoff 150 ms, rate limit folgado, circuito difícil de abrir. */
  private static ErpClientConfig folgada() {
    return ErpClientConfig.padrao()
        .withBackoff(Duration.ofMillis(150), Duration.ofSeconds(2))
        .withRateLimit(100, Duration.ofSeconds(1), Duration.ofSeconds(5))
        .withCircuit(100, 50, 50f, Duration.ofSeconds(30));
  }

  private static long requisicoes(String caminho) {
    return erp.countRequestsMatching(getRequestedFor(urlEqualTo(caminho)).build()).getCount();
  }

  private void primeiro429DepoisOk(String retryAfter) {
    var primeira = aResponse().withStatus(429);
    if (retryAfter != null) {
      primeira.withHeader("Retry-After", retryAfter);
    }
    erp.stubFor(
        get(urlEqualTo(PATH))
            .inScenario("429")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(primeira)
            .willSetStateTo("liberado"));
    erp.stubFor(
        get(urlEqualTo(PATH))
            .inScenario("429")
            .whenScenarioStateIs("liberado")
            .willReturn(aResponse().withStatus(200).withBody("ok")));
  }

  @Test
  @DisplayName("Dado um 429 / Quando o cliente chama / Então aplica backoff e repete até dar certo")
  void em429AplicaBackoff() {
    primeiro429DepoisOk(null);

    long inicio = System.nanoTime();
    String corpo = cliente(folgada()).get(String.class, PATH).block();
    long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

    assertThat(corpo).isEqualTo("ok");
    assertThat(requisicoes(PATH)).isEqualTo(2);
    assertThat(ms).as("esperou o backoff de 150 ms").isGreaterThanOrEqualTo(140);
  }

  @Test
  @DisplayName(
      "Dado 429 com Retry-After maior que o backoff / Quando repete / Então espera o prazo inteiro")
  void em429RespeitaRetryAfterSemCortarNoBackoff() {
    primeiro429DepoisOk("1");
    // maxBackoff de 100 ms: se o Retry-After fosse cortado nele, a espera seria ~100 ms.
    ErpClientConfig cfg =
        folgada()
            .withBackoff(Duration.ofMillis(50), Duration.ofMillis(100))
            .withMaxRetryAfter(Duration.ofSeconds(5));

    long inicio = System.nanoTime();
    String corpo = cliente(cfg).get(String.class, PATH).block();
    long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

    assertThat(corpo).isEqualTo("ok");
    assertThat(ms).as("Retry-After: 1 (s)").isGreaterThanOrEqualTo(950);
  }

  @Test
  @DisplayName("Dado Retry-After acima do teto / Quando recebe o 429 / Então falha sem repetir")
  void retryAfterAcimaDoTetoNaoRepete() {
    primeiro429DepoisOk("120");
    ErpClientConfig cfg = folgada().withMaxRetryAfter(Duration.ofSeconds(5));

    assertThatThrownBy(() -> cliente(cfg).get(String.class, PATH).block())
        .isInstanceOf(WebClientResponseException.TooManyRequests.class);

    assertThat(requisicoes(PATH)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Dado 429 e rate limiter de 1 por 600 ms / Quando repete / Então a tentativa espera permissão")
  void em429ORetryRespeitaORateLimiter() {
    primeiro429DepoisOk(null); // backoff de só 150 ms: quem segura a 2ª tentativa é o limiter

    long inicio = System.nanoTime();
    String corpo =
        cliente(folgada().withRateLimit(1, Duration.ofMillis(600), Duration.ofSeconds(5)))
            .get(String.class, PATH)
            .block();
    long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

    assertThat(corpo).isEqualTo("ok");
    assertThat(requisicoes(PATH)).isEqualTo(2);
    assertThat(ms).as("2ª tentativa só após a renovação do limiter").isGreaterThanOrEqualTo(550);
  }

  @Test
  @DisplayName(
      "Dado rate limiter de 2 por 600 ms / Quando 3 chamadas seguidas / Então a 3ª espera a janela")
  void rateLimiterSegura() {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withBody("ok")));
    ResilientErpClient c =
        cliente(folgada().withRateLimit(2, Duration.ofMillis(600), Duration.ofSeconds(5)));

    long inicio = System.nanoTime();
    for (int i = 0; i < 3; i++) {
      assertThat(c.get(String.class, PATH).block()).isEqualTo("ok");
    }
    long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

    assertThat(ms).isGreaterThanOrEqualTo(550);
    assertThat(requisicoes(PATH)).isEqualTo(3);
  }

  @Test
  @DisplayName("Dado 429 sempre / Quando esgota as tentativas / Então propaga o 429")
  void em429EsgotaTentativas() {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(429)));

    assertThatThrownBy(() -> cliente(folgada()).get(String.class, PATH).block())
        .isInstanceOf(WebClientResponseException.TooManyRequests.class);
    assertThat(requisicoes(PATH)).isEqualTo(3);
  }

  @Test
  @DisplayName("Dado 404 / Quando chama / Então não repete e o circuito não conta")
  void erroDeClienteNaoRepete() {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(404)));
    ResilientErpClient c = cliente(folgada());

    assertThatThrownBy(() -> c.get(String.class, PATH).block())
        .isInstanceOf(WebClientResponseException.NotFound.class);

    assertThat(requisicoes(PATH)).isEqualTo(1);
    assertThat(c.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  @DisplayName("Dado 5xx repetido / Quando o circuito abre / Então para de chamar o ERP")
  void circuitoAbreEPoupaOErp() {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
    ResilientErpClient c =
        cliente(folgada().withMaxAttempts(1).withCircuit(4, 4, 50f, Duration.ofSeconds(30)));

    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> c.get(String.class, PATH).block())
          .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
    }
    assertThat(c.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

    assertThatThrownBy(() -> c.get(String.class, PATH).block())
        .isInstanceOf(CallNotPermittedException.class);
    assertThat(requisicoes(PATH)).as("a 5ª chamada nem saiu").isEqualTo(4);
  }

  @Test
  @DisplayName(
      "Dado 429 misturado a 5xx / Quando chamo / Então o circuito abre pelo limiar dos 5xx")
  void mistura429Com5xxAbreOCircuitoPeloLimiarDos5xx() {
    erp.stubFor(get(urlEqualTo("/limitado")).willReturn(aResponse().withStatus(429)));
    erp.stubFor(get(urlEqualTo("/fora")).willReturn(aResponse().withStatus(503)));
    // janela 4, mínimo 2, limiar 75%: se o 429 contasse como sucesso, 2 falhas em 4 = 50% e o
    // circuito NÃO abriria; ignorado, são 2 falhas em 2 gravadas = 100% e abre.
    ResilientErpClient c =
        cliente(folgada().withMaxAttempts(1).withCircuit(4, 2, 75f, Duration.ofSeconds(30)));

    for (String caminho : new String[] {"/limitado", "/limitado", "/fora", "/fora"}) {
      assertThatThrownBy(() -> c.get(String.class, caminho).block())
          .isInstanceOf(WebClientResponseException.class);
    }

    assertThat(c.circuitBreaker().getMetrics().getNumberOfSuccessfulCalls()).isZero();
    assertThat(c.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  @DisplayName(
      "Dado um ERP que pendura / Quando estoura o timeout / Então repete e abre o circuito")
  void timeoutRepeteEAbreOCircuito() {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(800)));
    ResilientErpClient c =
        cliente(
            folgada()
                .withMaxAttempts(2)
                .withCallTimeout(Duration.ofMillis(200))
                .withBackoff(Duration.ofMillis(10), Duration.ofMillis(10))
                .withCircuit(2, 2, 50f, Duration.ofSeconds(30)));

    assertThatThrownBy(() -> c.get(String.class, PATH).block())
        .hasRootCauseInstanceOf(TimeoutException.class);

    assertThat(requisicoes(PATH)).as("repetiu uma vez").isEqualTo(2);
    assertThat(c.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    assertThat(c.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  @DisplayName("Dado conexão recusada / Quando chama / Então é erro de I/O repetível")
  void erroDeIoRepete() {
    int portaFechada;
    try (ServerSocket livre = new ServerSocket(0)) {
      portaFechada = livre.getLocalPort(); // fechada logo em seguida: ninguém escuta nela
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    WebClient web =
        WebClient.builder()
            .baseUrl("http://localhost:" + portaFechada)
            .clientConnector(new JdkClientHttpConnector())
            .build();
    ResilientErpClient c = new ResilientErpClient("erp-io", web, folgada());

    assertThatThrownBy(() -> c.get(String.class, PATH).block())
        .hasRootCauseInstanceOf(IOException.class);
    assertThat(c.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
  }

  @Test
  @DisplayName("Dado o backoff / Quando calculo a espera / Então dobra, tem teto e lê Retry-After")
  void calculoDaEspera() {
    ErpClientConfig cfg =
        folgada()
            .withBackoff(Duration.ofMillis(100), Duration.ofMillis(350))
            .withMaxRetryAfter(Duration.ofSeconds(30));
    Instant agora = Instant.parse("2026-11-10T12:00:00Z");

    assertThat(ResilientErpClient.esperaMs(1, null, cfg, agora)).isEqualTo(100);
    assertThat(ResilientErpClient.esperaMs(2, null, cfg, agora)).isEqualTo(200);
    assertThat(ResilientErpClient.esperaMs(3, null, cfg, agora)).isEqualTo(350);
    assertThat(ResilientErpClient.esperaMs(99, null, cfg, agora)).isEqualTo(350);

    assertThat(ResilientErpClient.esperaMs(1, erro429(null), cfg, agora)).isEqualTo(100);
    // Retry-After maior que o teto do backoff vale inteiro (até maxRetryAfter).
    assertThat(ResilientErpClient.esperaMs(1, erro429("9"), cfg, agora)).isEqualTo(9000);
    assertThat(ResilientErpClient.esperaMs(1, erro429("abc"), cfg, agora)).isEqualTo(100);
    assertThat(ResilientErpClient.esperaMs(1, erro429(""), cfg, agora)).isEqualTo(100);
  }

  @Test
  @DisplayName("Dado Retry-After em data HTTP / Quando interpreto / Então vira a espera até a data")
  void retryAfterEmDataHttp() {
    Instant agora = Instant.parse("2026-11-10T12:00:00Z");
    HttpHeaders futuro = new HttpHeaders();
    futuro.add("Retry-After", "Tue, 10 Nov 2026 12:00:03 GMT");
    HttpHeaders passado = new HttpHeaders();
    passado.add("Retry-After", "Tue, 10 Nov 2026 11:00:00 GMT");

    assertThat(ResilientErpClient.retryAfterMs(futuro, agora)).isEqualTo(OptionalLong.of(3000));
    assertThat(ResilientErpClient.retryAfterMs(passado, agora)).isEqualTo(OptionalLong.of(0));
    assertThat(ResilientErpClient.retryAfterMs(new HttpHeaders(), agora)).isEmpty();
  }

  @Test
  @DisplayName("Dado Retry-After em data acima do teto / Quando decide / Então não é repetível")
  void retryAfterEmDataAcimaDoTetoNaoRepete() {
    Instant agora = Instant.parse("2026-11-10T12:00:00Z");
    ErpClientConfig cfg = folgada().withMaxRetryAfter(Duration.ofSeconds(10));
    HttpHeaders longe = new HttpHeaders();
    longe.add("Retry-After", "Tue, 10 Nov 2026 12:05:00 GMT");
    HttpHeaders perto = new HttpHeaders();
    perto.add("Retry-After", "Tue, 10 Nov 2026 12:00:05 GMT");

    assertThat(ResilientErpClient.repetivel(erro(429, longe), cfg, agora)).isFalse();
    assertThat(ResilientErpClient.repetivel(erro(429, perto), cfg, agora)).isTrue();
    assertThat(ResilientErpClient.repetivel(erro(404, new HttpHeaders()), cfg, agora)).isFalse();
  }

  private static WebClientResponseException erro429(String retryAfter) {
    HttpHeaders h = new HttpHeaders();
    if (retryAfter != null) {
      h.add("Retry-After", retryAfter);
    }
    return erro(429, h);
  }

  private static WebClientResponseException erro(int status, HttpHeaders headers) {
    return WebClientResponseException.create(status, "x", headers, new byte[0], null);
  }
}
