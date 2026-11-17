package com.recorra.driver.sdk;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Cliente HTTP de leitura do ERP (drivers só LEEM do ERP) com Resilience4j sobre o {@code
 * WebClient}. Camadas, de fora para dentro: <b>retry</b> → <b>circuit breaker</b> → <b>rate
 * limiter</b> → <b>timeout por chamada</b> → chamada. Como o rate limiter fica por dentro do retry,
 * cada nova tentativa também paga uma permissão: o backoff após um 429 nunca fura o limite do ERP.
 *
 * <ul>
 *   <li>Timeout: cada tentativa tem {@link ErpClientConfig#callTimeout()}; estourar repete e conta
 *       para o circuit breaker (sem isso um ERP que pendura prenderia o driver).
 *   <li>Retry: HTTP 429, 5xx, timeout e falha de I/O; backoff exponencial, ou o {@code Retry-After}
 *       do ERP (segundos ou data HTTP) quando for maior. Um {@code Retry-After} acima de {@link
 *       ErpClientConfig#maxRetryAfter()} não é repetido: falha na hora. Demais 4xx não repetem.
 *   <li>Circuit breaker: conta 5xx, timeout e I/O; 4xx (incl. 429) e o {@code RequestNotPermitted}
 *       do limiter são ignorados (nem sucesso, nem falha).
 * </ul>
 *
 * <p>Retry: Resilience4j e não o {@code @Retryable} do Spring 7, porque o retry precisa enxergar a
 * exceção HTTP (para o {@code Retry-After}), compor com os outros operadores e funcionar sobre
 * {@code Mono} sem proxy de método. Resilience4j 2.4.0 usa só os módulos core (sem Spring).
 *
 * <p><b>Logs:</b> só nome do cliente, tentativa, espera e status HTTP ou classe da exceção. Nunca
 * URI, query, headers nem corpo (a mensagem das exceções do WebClient traz a URI, então ela também
 * não é logada). O erro final é logado uma única vez na borda (REC-005).
 *
 * <p><b>Para o driver:</b> o {@code WebClient} recebido define URL base e autenticação; se as
 * respostas puderem passar de 256 KB (limite padrão de {@code maxInMemorySize}), configure o limite
 * ou, melhor, pagine a leitura com páginas pequenas.
 *
 * <p>Sem regra de negócio e sem ERP específico.
 */
public final class ResilientErpClient {

  private static final Logger LOG = LoggerFactory.getLogger(ResilientErpClient.class);

  private final WebClient web;
  private final Duration callTimeout;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiter;

  public ResilientErpClient(String nome, WebClient web, ErpClientConfig config) {
    Objects.requireNonNull(nome, "nome");
    this.web = Objects.requireNonNull(web, "web");
    Objects.requireNonNull(config, "config");
    this.callTimeout = config.callTimeout();
    this.retry = criarRetry(nome, config);
    this.circuitBreaker = criarCircuitBreaker(nome, config);
    this.rateLimiter = criarRateLimiter(nome, config);
    registrarLogs(nome, config);
  }

  /** GET resiliente; {@code uriTemplate} aceita variáveis de URI. */
  public <T> Mono<T> get(Class<T> tipo, String uriTemplate, Object... variaveis) {
    return Mono.defer(
            () ->
                web.get()
                    .uri(uriTemplate, variaveis)
                    .retrieve()
                    .bodyToMono(tipo)
                    .timeout(callTimeout))
        .transformDeferred(RateLimiterOperator.of(rateLimiter))
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .transformDeferred(RetryOperator.of(retry));
  }

  public CircuitBreaker circuitBreaker() {
    return circuitBreaker;
  }

  public RateLimiter rateLimiter() {
    return rateLimiter;
  }

  private void registrarLogs(String nome, ErpClientConfig config) {
    retry
        .getEventPublisher()
        .onRetry(
            e ->
                LOG.warn(
                    "ERP {}: tentativa {}/{} falhou ({}); nova tentativa em {} ms",
                    nome,
                    e.getNumberOfRetryAttempts(),
                    config.maxAttempts(),
                    descricao(e.getLastThrowable()),
                    e.getWaitInterval().toMillis()))
        .onError(
            e ->
                LOG.warn(
                    "ERP {}: tentativas esgotadas ({}/{}); último erro: {}",
                    nome,
                    e.getNumberOfRetryAttempts(),
                    config.maxAttempts(),
                    descricao(e.getLastThrowable())))
        .onIgnoredError(
            e -> {
              // Erro não repetível: só 429/5xx com Retry-After acima do limite merecem rastro (os
              // outros 4xx são erro de quem chama e seguem sem log).
              if (e.getLastThrowable() instanceof WebClientResponseException http
                  && (http.getStatusCode().value() == 429
                      || http.getStatusCode().is5xxServerError())) {
                LOG.warn(
                    "ERP {}: HTTP {} sem nova tentativa: Retry-After pedido de {} ms excede o limite"
                        + " de {} ms",
                    nome,
                    http.getStatusCode().value(),
                    retryAfterMs(http.getHeaders(), Instant.now()).orElse(0),
                    config.maxRetryAfter().toMillis());
              }
            });
    circuitBreaker
        .getEventPublisher()
        .onStateTransition(
            e ->
                LOG.warn(
                    "ERP {}: circuit breaker {} -> {}",
                    nome,
                    e.getStateTransition().getFromState(),
                    e.getStateTransition().getToState()));
    rateLimiter
        .getEventPublisher()
        .onFailure(e -> LOG.warn("ERP {}: rate limiter negou a chamada (sem permissão)", nome));
  }

  /** Status HTTP ou classe da exceção: nunca a mensagem (ela traz a URI). */
  static String descricao(Throwable t) {
    if (t instanceof WebClientResponseException e) {
      return "HTTP " + e.getStatusCode().value();
    }
    return t == null ? "desconhecido" : t.getClass().getSimpleName();
  }

  private static Retry criarRetry(String nome, ErpClientConfig c) {
    RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(c.maxAttempts())
            .retryOnException(t -> repetivel(t, c, Instant.now()))
            .intervalBiFunction(
                (tentativa, resultado) ->
                    esperaMs(tentativa, resultado.swap().getOrNull(), c, Instant.now()))
            .build();
    return Retry.of(nome, config);
  }

  private static CircuitBreaker criarCircuitBreaker(String nome, ErpClientConfig c) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowSize(c.circuitWindowSize())
            .minimumNumberOfCalls(c.circuitMinimumCalls())
            .failureRateThreshold(c.circuitFailureRate())
            .waitDurationInOpenState(c.circuitOpenDuration())
            // Só falha do ERP conta; o resto (4xx, 429, RequestNotPermitted) é ignorado: nem
            // sucesso nem falha, para não diluir a taxa de falha.
            .ignoreException(t -> !falhaDoErp(t))
            .build();
    return CircuitBreaker.of(nome, config);
  }

  private static RateLimiter criarRateLimiter(String nome, ErpClientConfig c) {
    RateLimiterConfig config =
        RateLimiterConfig.custom()
            .limitForPeriod(c.rateLimitForPeriod())
            .limitRefreshPeriod(c.rateLimitPeriod())
            .timeoutDuration(c.rateLimitTimeout())
            .build();
    return RateLimiter.of(nome, config);
  }

  /**
   * Vale repetir: 429 e 5xx (salvo {@code Retry-After} acima de {@code maxRetryAfter}), timeout e
   * falha de I/O.
   */
  static boolean repetivel(Throwable t, ErpClientConfig c, Instant agora) {
    if (t instanceof WebClientResponseException e) {
      boolean statusRepetivel =
          e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError();
      if (!statusRepetivel) {
        return false;
      }
      OptionalLong pedido = retryAfterMs(e.getHeaders(), agora);
      return pedido.isEmpty() || pedido.getAsLong() <= c.maxRetryAfter().toMillis();
    }
    return t instanceof TimeoutException || t instanceof WebClientRequestException;
  }

  /**
   * Conta para o circuit breaker: o ERP falhou (5xx), estourou o timeout ou não respondeu (I/O).
   */
  static boolean falhaDoErp(Throwable t) {
    if (t instanceof WebClientResponseException e) {
      return e.getStatusCode().is5xxServerError();
    }
    return t instanceof TimeoutException || t instanceof WebClientRequestException;
  }

  /**
   * Backoff exponencial (dobra a cada tentativa, com teto {@code maxBackoff}) ou o {@code
   * Retry-After} do ERP, se maior (já garantido {@code <= maxRetryAfter} por {@link #repetivel}).
   */
  static long esperaMs(int tentativa, Throwable erro, ErpClientConfig c, Instant agora) {
    long exponencial = c.initialBackoff().toMillis() << Math.min(Math.max(tentativa - 1, 0), 20);
    long espera = Math.min(exponencial, c.maxBackoff().toMillis());
    if (erro instanceof WebClientResponseException e) {
      espera = Math.max(espera, retryAfterMs(e.getHeaders(), agora).orElse(0));
    }
    return espera;
  }

  /**
   * {@code Retry-After} em segundos ou como data HTTP (RFC 1123), em ms a partir de {@code agora}.
   * Ausente ou ilegível = vazio (ilegível vai para DEBUG, sem PII: é só o valor do header).
   */
  static OptionalLong retryAfterMs(HttpHeaders cabecalhos, Instant agora) {
    String valor = cabecalhos.getFirst(HttpHeaders.RETRY_AFTER);
    if (valor == null) {
      return OptionalLong.empty();
    }
    String v = valor.trim();
    try {
      return OptionalLong.of(Math.max(0, Long.parseLong(v)) * 1000);
    } catch (NumberFormatException e) {
      try {
        Instant data =
            ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC))
                .toInstant();
        return OptionalLong.of(Math.max(0, Duration.between(agora, data).toMillis()));
      } catch (DateTimeParseException e2) {
        LOG.debug("Retry-After ilegível ignorado: '{}'", v);
        return OptionalLong.empty();
      }
    }
  }
}
