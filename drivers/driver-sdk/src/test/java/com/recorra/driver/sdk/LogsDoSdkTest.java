package com.recorra.driver.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Logs do SDK: níveis certos e NADA de URI, query, headers nem corpo (regra do usuário: sem PII em
 * log; a mensagem das exceções do WebClient traz a URI).
 */
class LogsDoSdkTest {

  @RegisterExtension
  static WireMockExtension erp =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.options().dynamicPort())
          .build();

  /** Path e query com "PII" para provar que não vazam. */
  private static final String PATH_SECRETO = "/clientes/segredo-cpf-12345678900?token=abc123";

  private static final String CORPO_SECRETO = "corpo-com-dados-pessoais";

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

  private ResilientErpClient cliente(ErpClientConfig config) {
    WebClient web =
        WebClient.builder()
            .baseUrl(erp.baseUrl())
            .clientConnector(new JdkClientHttpConnector())
            .build();
    return new ResilientErpClient("erp-teste", web, config);
  }

  private ErpClientConfig rapida() {
    return ErpClientConfig.padrao()
        .withBackoff(Duration.ofMillis(10), Duration.ofMillis(20))
        .withRateLimit(100, Duration.ofSeconds(1), Duration.ofSeconds(1))
        .withCircuit(100, 50, 50f, Duration.ofSeconds(30));
  }

  private List<ILoggingEvent> eventos(Level nivel) {
    return logs.list.stream().filter(e -> e.getLevel() == nivel).toList();
  }

  private void semVazamento() {
    for (ILoggingEvent e : logs.list) {
      String texto = e.getFormattedMessage() + " " + e.getThrowableProxy();
      assertThat(texto)
          .doesNotContain("segredo-cpf")
          .doesNotContain("12345678900")
          .doesNotContain("abc123")
          .doesNotContain("token")
          .doesNotContain("/clientes")
          .doesNotContain("localhost")
          .doesNotContain("127.0.0.1")
          .doesNotContain(String.valueOf(erp.getPort()))
          .doesNotContain(CORPO_SECRETO);
    }
  }

  @Test
  @DisplayName(
      "Dado um 429 e depois ok / Quando repete / Então WARN com cliente, tentativa, espera e status")
  void retryLogaWarnSemUri() {
    erp.stubFor(
        get(urlEqualTo(PATH_SECRETO))
            .inScenario("x")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(429).withBody(CORPO_SECRETO))
            .willSetStateTo("ok"));
    erp.stubFor(
        get(urlEqualTo(PATH_SECRETO))
            .inScenario("x")
            .whenScenarioStateIs("ok")
            .willReturn(aResponse().withStatus(200).withBody("ok")));

    cliente(rapida()).get(String.class, PATH_SECRETO).block();

    assertThat(eventos(Level.WARN))
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("erp-teste", "tentativa 1/3", "HTTP 429", "10 ms");
    assertThat(eventos(Level.ERROR)).isEmpty();
    semVazamento();
  }

  @Test
  @DisplayName(
      "Dado 5xx sempre / Quando esgota as tentativas / Então WARN de esgotadas (e nenhum ERROR)")
  void esgotarLogaWarnSemUri() {
    erp.stubFor(
        get(urlEqualTo(PATH_SECRETO))
            .willReturn(aResponse().withStatus(503).withBody(CORPO_SECRETO)));

    assertThatThrownBy(() -> cliente(rapida()).get(String.class, PATH_SECRETO).block())
        .isInstanceOf(RuntimeException.class);

    List<String> warns =
        eventos(Level.WARN).stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(warns).hasSize(3); // 2 retries + esgotadas
    assertThat(warns.getLast()).contains("esgotadas", "3/3", "HTTP 503");
    assertThat(eventos(Level.ERROR)).as("o ERROR único é da borda (REC-005)").isEmpty();
    semVazamento();
  }

  @Test
  @DisplayName(
      "Dado 429 com Retry-After acima do limite / Quando falha sem repetir / Então 1 WARN com o motivo")
  void retryAfterAcimaDoLimiteLogaWarn() {
    erp.stubFor(
        get(urlEqualTo(PATH_SECRETO))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withHeader("Retry-After", "120")
                    .withBody(CORPO_SECRETO)));

    assertThatThrownBy(
            () ->
                cliente(rapida().withMaxRetryAfter(Duration.ofSeconds(5)))
                    .get(String.class, PATH_SECRETO)
                    .block())
        .isInstanceOf(RuntimeException.class);

    assertThat(eventos(Level.WARN))
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("erp-teste", "HTTP 429", "120000 ms", "5000 ms");
    semVazamento();
  }

  @Test
  @DisplayName("Dado 404 / Quando chama / Então nada de WARN de retry (não é repetível)")
  void erroDeClienteNaoLogaRetry() {
    erp.stubFor(get(urlEqualTo(PATH_SECRETO)).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> cliente(rapida()).get(String.class, PATH_SECRETO).block())
        .isInstanceOf(RuntimeException.class);

    assertThat(eventos(Level.WARN)).isEmpty();
    semVazamento();
  }

  @Test
  @DisplayName("Dado o circuito abrindo / Quando transiciona / Então WARN com nome e de→para")
  void circuitBreakerLogaTransicao() {
    erp.stubFor(get(urlEqualTo(PATH_SECRETO)).willReturn(aResponse().withStatus(503)));
    ResilientErpClient c =
        cliente(rapida().withMaxAttempts(1).withCircuit(2, 2, 50f, Duration.ofSeconds(30)));

    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(() -> c.get(String.class, PATH_SECRETO).block())
          .isInstanceOf(RuntimeException.class);
    }

    assertThat(eventos(Level.WARN))
        .anySatisfy(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains("erp-teste", "circuit breaker", "CLOSED -> OPEN"));
    semVazamento();
  }

  @Test
  @DisplayName("Dado o limiter sem permissão / Quando nega / Então WARN com o nome do cliente")
  void rateLimiterLogaNegacao() {
    erp.stubFor(
        get(urlEqualTo(PATH_SECRETO)).willReturn(aResponse().withStatus(200).withBody("ok")));
    ResilientErpClient c =
        cliente(rapida().withRateLimit(1, Duration.ofSeconds(30), Duration.ofMillis(50)));
    c.get(String.class, PATH_SECRETO).block();

    assertThatThrownBy(() -> c.get(String.class, PATH_SECRETO).block())
        .isInstanceOf(RuntimeException.class);

    assertThat(eventos(Level.WARN))
        .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("erp-teste", "rate limiter"));
    semVazamento();
  }

  @Test
  @DisplayName("Dado Retry-After ilegível / Quando interpreta / Então DEBUG com o valor do header")
  void retryAfterIlegivelLogaDebug() {
    erp.stubFor(
        get(urlEqualTo(PATH_SECRETO))
            .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "logo-logo")));

    assertThatThrownBy(
            () -> cliente(rapida().withMaxAttempts(2)).get(String.class, PATH_SECRETO).block())
        .isInstanceOf(RuntimeException.class);

    assertThat(eventos(Level.DEBUG))
        .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("logo-logo"));
    semVazamento();
  }

  @Test
  @DisplayName(
      "Dado assinatura inválida / Quando chega o webhook / Então WARN só com a classe do receptor")
  void webhookRejeitadoLogaWarnSemCabecalhoNemCorpo() {
    WebhookReceiver receptor =
        new WebhookReceiver((evento, chave) -> PublishResult.ACCEPTED) {
          @Override
          protected boolean assinaturaValida(Map<String, List<String>> cabecalhos, byte[] corpo) {
            return false;
          }

          @Override
          protected List<CanonicalMessage> traduzir(byte[] corpo) {
            return List.of();
          }
        };

    receptor.receive(
        Map.of("X-Signature", List.of("segredo-cpf-12345678900")), CORPO_SECRETO.getBytes());

    assertThat(eventos(Level.WARN))
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("assinatura inválida", "LogsDoSdkTest");
    semVazamento();
  }

  @Test
  @DisplayName(
      "Dado falha ao publicar / Quando o poller roda / Então WARN com chave e posição e relança; DEBUG no fim")
  void pollerLogaFalhaEFimDeRodada() {
    CanonicalMessage m1 =
        new CanonicalMessage(CORPO_SECRETO, IdempotencyKey.paraVenda("c1", "erp", "1"));
    CanonicalMessage m2 =
        new CanonicalMessage(CORPO_SECRETO, IdempotencyKey.paraVenda("c1", "erp", "2"));
    CursorStore cursores = new EsqueletosDeDriverTest.CursorEmMemoria();
    IncrementalPoller falha =
        new IncrementalPoller(
            cursores,
            (evento, chave) -> {
              if (chave.valor().endsWith(":2")) {
                throw new IllegalStateException(CORPO_SECRETO + " " + PATH_SECRETO);
              }
              return PublishResult.ACCEPTED;
            }) {
          @Override
          protected Page fetchSince(String cursor) {
            return new Page(List.of(m1, m2), "c2");
          }
        };

    assertThatThrownBy(falha::pollOnce).isInstanceOf(IllegalStateException.class);

    assertThat(eventos(Level.WARN))
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("c1:erp:2", "2/2", "cursor não avança");

    IncrementalPoller ok =
        new IncrementalPoller(cursores, (evento, chave) -> PublishResult.ACCEPTED) {
          @Override
          protected Page fetchSince(String cursor) {
            return new Page(List.of(m1, m2), "c2");
          }
        };
    ok.pollOnce();

    assertThat(eventos(Level.DEBUG))
        .anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("2 evento(s)"));
    semVazamento();
  }
}
