package com.recorra.driver.sdk;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.recorra.common.trace.ContextoDeTrabalho;
import com.recorra.common.trace.MdcEscopo;
import com.recorra.common.trace.TraceContext;
import com.recorra.common.trace.Traceparent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

/** Ponto de extensão do traceparent (REC-005): decorador do publisher e filtro de saída. */
@SuppressWarnings("try")
class TracingSdkTest {

  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

  @RegisterExtension
  static WireMockExtension core =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.options().dynamicPort())
          .build();

  @Test
  @DisplayName(
      "Dado um trace corrente / Quando publica / Então o transporte vê o mesmo trace com novo span, e o contexto é restaurado")
  void decoradorAbreNovoSpanDoMesmoTrace() {
    List<Traceparent> vistos = new ArrayList<>();
    CanonicalPublisher transporte =
        (evento, chave) -> {
          vistos.add(TraceContext.atual().orElseThrow());
          return PublishResult.ACCEPTED;
        };
    CanonicalPublisher publisher = new TracingCanonicalPublisher(transporte);

    try (MdcEscopo e = ContextoDeTrabalho.restaurar("A", TRACE)) {
      String spanAntes = MDC.get("spanId");
      PublishResult r = publisher.publish("evento", IdempotencyKey.paraVenda("A", "erp", "1"));

      assertThat(r).isEqualTo(PublishResult.ACCEPTED);
      assertThat(vistos)
          .singleElement()
          .satisfies(
              t -> {
                assertThat(t.traceId()).isEqualTo(TRACE);
                assertThat(t.spanId()).isNotEqualTo(spanAntes);
              });
      assertThat(MDC.get("spanId")).isEqualTo(spanAntes);
    }
  }

  @Test
  @DisplayName(
      "Dado nenhum trace / Quando publica / Então abre um trace novo só durante a publicação")
  void semTraceCriaUmEFecha() {
    List<Traceparent> vistos = new ArrayList<>();
    CanonicalPublisher publisher =
        new TracingCanonicalPublisher(
            (evento, chave) -> {
              vistos.add(TraceContext.atual().orElseThrow());
              return PublishResult.DUPLICATE;
            });

    assertThat(publisher.publish("e", IdempotencyKey.paraVenda("A", "erp", "1")))
        .isEqualTo(PublishResult.DUPLICATE);

    assertThat(vistos).hasSize(1);
    assertThat(TraceContext.atual()).isEmpty();
  }

  @Test
  @DisplayName("Dado uma exceção do transporte / Quando publica / Então o MDC volta ao que era")
  void excecaoRestauraOContexto() {
    CanonicalPublisher publisher =
        new TracingCanonicalPublisher(
            (evento, chave) -> {
              throw new IllegalStateException("core fora");
            });

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> publisher.publish("e", IdempotencyKey.paraVenda("A", "erp", "1")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(TraceContext.atual()).isEmpty();
  }

  @Test
  @DisplayName(
      "Dado um trace corrente / Quando o WebClient chama o core / Então vai o traceparent com o mesmo trace")
  void filtroDeSaidaEnviaOHeader() {
    core.stubFor(
        get(urlEqualTo("/ingestao")).willReturn(aResponse().withStatus(200).withBody("ok")));
    WebClient web =
        WebClient.builder()
            .baseUrl(core.baseUrl())
            .clientConnector(new JdkClientHttpConnector())
            .filter(new TraceparentExchangeFilter())
            .build();

    try (MdcEscopo e = ContextoDeTrabalho.restaurar("A", TRACE)) {
      web.get().uri("/ingestao").retrieve().bodyToMono(String.class).block();
    }

    core.verify(
        getRequestedFor(urlEqualTo("/ingestao"))
            .withHeader("traceparent", matching("00-" + TRACE + "-[0-9a-f]{16}-01")));
  }

  @Test
  @DisplayName("Dado nenhum trace / Quando o WebClient chama / Então não inventa header")
  void filtroSemTraceNaoEnviaHeader() {
    core.stubFor(
        get(urlEqualTo("/sem-trace")).willReturn(aResponse().withStatus(200).withBody("ok")));
    WebClient web =
        WebClient.builder()
            .baseUrl(core.baseUrl())
            .clientConnector(new JdkClientHttpConnector())
            .filter(new TraceparentExchangeFilter())
            .build();

    web.get().uri("/sem-trace").retrieve().bodyToMono(String.class).block();

    core.verify(getRequestedFor(urlEqualTo("/sem-trace")).withoutHeader("traceparent"));
  }

  @Test
  @DisplayName(
      "Dado 503 e depois 200 com MDC no chamador / Quando o cliente repete / Então as 2 requisições levam o mesmo traceId e o WARN do retry tem traceId e comercioId do chamador")
  void mdcDoChamadorSobreviveAoRetryNoReactor() {
    core.stubFor(
        get(urlEqualTo("/retry"))
            .inScenario("r")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("ok"));
    core.stubFor(
        get(urlEqualTo("/retry"))
            .inScenario("r")
            .whenScenarioStateIs("ok")
            .willReturn(aResponse().withStatus(200).withBody("ok")));
    WebClient web =
        WebClient.builder()
            .baseUrl(core.baseUrl())
            .clientConnector(new JdkClientHttpConnector())
            .filter(new TraceparentExchangeFilter())
            .build();
    ResilientErpClient cliente =
        new ResilientErpClient(
            "erp-mdc",
            web,
            ErpClientConfig.padrao()
                .withBackoff(Duration.ofMillis(20), Duration.ofMillis(40))
                .withRateLimit(100, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    Logger sdk = (Logger) LoggerFactory.getLogger("com.recorra.driver.sdk");
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    logs.start();
    sdk.addAppender(logs);
    try {
      try (MdcEscopo e = ContextoDeTrabalho.restaurar("comercio-A", TRACE)) {
        assertThat(cliente.get(String.class, "/retry").block()).isEqualTo("ok");
      }
    } finally {
      sdk.detachAppender(logs);
    }

    var requisicoes = core.findAll(getRequestedFor(urlEqualTo("/retry")));
    assertThat(requisicoes).hasSize(2);
    assertThat(requisicoes)
        .allSatisfy(r -> assertThat(r.getHeader("traceparent")).startsWith("00-" + TRACE + "-"));
    assertThat(logs.list.stream().filter(e -> e.getLevel() == Level.WARN))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getMDCPropertyMap())
                  .containsEntry("traceId", TRACE)
                  .containsEntry("comercioId", "comercio-A");
              assertThat(e.getFormattedMessage()).contains("erp-mdc", "HTTP 503");
            });
    assertThat(TraceContext.atual()).as("a thread do chamador volta limpa").isEmpty();
  }
}
