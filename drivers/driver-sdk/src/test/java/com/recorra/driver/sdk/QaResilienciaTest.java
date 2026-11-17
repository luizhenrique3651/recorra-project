package com.recorra.driver.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** QA (REC-004): lacunas dos cenários do card (4xx variados, Retry-After exótico, logs de I/O). */
class QaResilienciaTest {

  @RegisterExtension
  static WireMockExtension erp =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.options().dynamicPort())
          .build();

  private static final String PATH = "/vendas/9";
  private static final String SEGREDO = "Bearer segredo-token-xyz";

  private final Logger sdk = (Logger) LoggerFactory.getLogger("com.recorra.driver.sdk");
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private Level nivelOriginal;

  @BeforeEach
  void captura() {
    erp.resetAll();
    nivelOriginal = sdk.getLevel();
    sdk.setLevel(Level.DEBUG);
    logs.start();
    sdk.addAppender(logs);
  }

  @AfterEach
  void solta() {
    sdk.detachAppender(logs);
    sdk.setLevel(nivelOriginal);
  }

  private ResilientErpClient cliente(String baseUrl, ErpClientConfig config) {
    WebClient web =
        WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", SEGREDO)
            .clientConnector(new JdkClientHttpConnector())
            .build();
    return new ResilientErpClient("erp-qa", web, config);
  }

  private static ErpClientConfig rapida() {
    return ErpClientConfig.padrao()
        .withBackoff(Duration.ofMillis(20), Duration.ofMillis(40))
        .withRateLimit(100, Duration.ofSeconds(1), Duration.ofSeconds(1))
        .withCircuit(100, 50, 50f, Duration.ofSeconds(30));
  }

  private static long requisicoes() {
    return erp.countRequestsMatching(getRequestedFor(urlEqualTo(PATH)).build()).getCount();
  }

  private void semVazamento() {
    assertThat(logs.list).isNotEmpty();
    for (ILoggingEvent e : logs.list) {
      assertThat(e.getFormattedMessage() + " " + e.getThrowableProxy())
          .doesNotContain("segredo-token")
          .doesNotContain("Authorization")
          .doesNotContain("Bearer")
          .doesNotContain("localhost")
          .doesNotContain("127.0.0.1")
          .doesNotContain(PATH);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 409, 422})
  @DisplayName(
      "Dado um 4xx de cliente / Quando chama / Então 1 só requisição e o circuito não conta")
  void quatroXxNaoRepeteENaoContaNoCircuito(int status) {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status)));
    ResilientErpClient c = cliente(erp.baseUrl(), rapida());

    assertThatThrownBy(() -> c.get(String.class, PATH).block())
        .isInstanceOfSatisfying(
            WebClientResponseException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(status));

    assertThat(requisicoes()).isEqualTo(1);
    assertThat(c.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
  }

  @Test
  @DisplayName("Dado 503 com Retry-After acima do teto / Quando chama / Então não repete")
  void cincoXxComRetryAfterAcimaDoTetoNaoRepete() {
    erp.stubFor(
        get(urlEqualTo(PATH))
            .willReturn(aResponse().withStatus(503).withHeader("Retry-After", "3600")));
    ResilientErpClient c =
        cliente(erp.baseUrl(), rapida().withMaxRetryAfter(Duration.ofSeconds(5)));

    assertThatThrownBy(() -> c.get(String.class, PATH).block())
        .isInstanceOf(WebClientResponseException.class);

    assertThat(requisicoes()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "abc", "", "1e3"})
  @DisplayName("Dado Retry-After zero/negativo/ilegível / Quando 429 / Então repete pelo backoff")
  void retryAfterExoticoCaiNoBackoff(String valor) {
    erp.stubFor(
        get(urlEqualTo(PATH))
            .inScenario("r")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", valor))
            .willSetStateTo("ok"));
    erp.stubFor(
        get(urlEqualTo(PATH))
            .inScenario("r")
            .whenScenarioStateIs("ok")
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    String corpo = cliente(erp.baseUrl(), rapida()).get(String.class, PATH).block();

    assertThat(corpo).isEqualTo("ok");
    assertThat(requisicoes()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "Dado conexão recusada / Quando esgota / Então logs sem host, porta, path nem header")
  void falhaDeIoNaoVazaNoLog() throws Exception {
    int portaFechada;
    try (ServerSocket s = new ServerSocket(0)) {
      portaFechada = s.getLocalPort();
    }
    ResilientErpClient c = cliente("http://localhost:" + portaFechada, rapida());

    assertThatThrownBy(() -> c.get(String.class, PATH).block()).isNotNull();

    semVazamento();
    assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.ERROR)).isEmpty();
  }

  @Test
  @DisplayName("Dado timeout / Quando esgota / Então logs sem host, path nem header")
  void timeoutNaoVazaNoLog() {
    erp.stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200).withFixedDelay(600)));
    ResilientErpClient c =
        cliente(erp.baseUrl(), rapida().withMaxAttempts(2).withCallTimeout(Duration.ofMillis(150)));

    assertThatThrownBy(() -> c.get(String.class, PATH).block()).isNotNull();

    semVazamento();
  }
}
