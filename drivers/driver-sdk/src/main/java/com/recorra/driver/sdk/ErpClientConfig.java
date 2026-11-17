package com.recorra.driver.sdk;

import java.time.Duration;
import java.util.Objects;

/**
 * Política de resiliência do {@link ResilientErpClient}: timeout por chamada, retry com backoff
 * exponencial, rate limiter (respeita o limite do ERP) e circuit breaker. Parta de {@link
 * #padrao()} e ajuste com os métodos {@code withX}.
 *
 * @param maxAttempts tentativas no total (1 = sem retry)
 * @param initialBackoff espera antes do 2º try; dobra a cada tentativa
 * @param maxBackoff teto do backoff exponencial
 * @param maxRetryAfter maior {@code Retry-After} do ERP que o cliente espera; um pedido maior que
 *     isso NÃO é repetido (falha na hora, quem chamou decide quando voltar)
 * @param callTimeout limite de cada tentativa (conexão + resposta); estourou = {@link
 *     java.util.concurrent.TimeoutException}, repetível e contada pelo circuit breaker
 * @param rateLimitForPeriod chamadas permitidas por {@code rateLimitPeriod}
 * @param rateLimitPeriod janela do rate limiter
 * @param rateLimitTimeout quanto esperar por uma permissão antes de falhar (0 = falha na hora)
 * @param circuitWindowSize chamadas na janela deslizante do circuit breaker
 * @param circuitMinimumCalls mínimo de chamadas antes de avaliar a taxa de falha
 * @param circuitFailureRate % de falha que abre o circuito (1..100)
 * @param circuitOpenDuration quanto tempo o circuito fica aberto antes de testar de novo
 */
public record ErpClientConfig(
    int maxAttempts,
    Duration initialBackoff,
    Duration maxBackoff,
    Duration maxRetryAfter,
    Duration callTimeout,
    int rateLimitForPeriod,
    Duration rateLimitPeriod,
    Duration rateLimitTimeout,
    int circuitWindowSize,
    int circuitMinimumCalls,
    float circuitFailureRate,
    Duration circuitOpenDuration) {

  public ErpClientConfig {
    Objects.requireNonNull(initialBackoff, "initialBackoff");
    Objects.requireNonNull(maxBackoff, "maxBackoff");
    Objects.requireNonNull(maxRetryAfter, "maxRetryAfter");
    Objects.requireNonNull(callTimeout, "callTimeout");
    Objects.requireNonNull(rateLimitPeriod, "rateLimitPeriod");
    Objects.requireNonNull(rateLimitTimeout, "rateLimitTimeout");
    Objects.requireNonNull(circuitOpenDuration, "circuitOpenDuration");
    exigir(maxAttempts >= 1, "maxAttempts >= 1");
    exigir(positivo(initialBackoff), "initialBackoff > 0");
    exigir(maxBackoff.compareTo(initialBackoff) >= 0, "maxBackoff >= initialBackoff");
    exigir(positivo(maxRetryAfter), "maxRetryAfter > 0");
    exigir(positivo(callTimeout), "callTimeout > 0");
    exigir(rateLimitForPeriod >= 1, "rateLimitForPeriod >= 1");
    exigir(positivo(rateLimitPeriod), "rateLimitPeriod > 0");
    exigir(!rateLimitTimeout.isNegative(), "rateLimitTimeout >= 0");
    exigir(circuitWindowSize >= 1, "circuitWindowSize >= 1");
    exigir(circuitMinimumCalls >= 1, "circuitMinimumCalls >= 1");
    exigir(circuitFailureRate >= 1 && circuitFailureRate <= 100, "circuitFailureRate em 1..100");
    exigir(positivo(circuitOpenDuration), "circuitOpenDuration > 0");
  }

  /**
   * Padrão conservador para um ERP com limite baixo: 3 tentativas, 2 req/s, timeout de 10 s por
   * chamada, aceita {@code Retry-After} de até 60 s.
   */
  public static ErpClientConfig padrao() {
    return new ErpClientConfig(
        3,
        Duration.ofMillis(500),
        Duration.ofSeconds(10),
        Duration.ofSeconds(60),
        Duration.ofSeconds(10),
        2,
        Duration.ofSeconds(1),
        Duration.ofSeconds(5),
        20,
        10,
        50f,
        Duration.ofSeconds(30));
  }

  public ErpClientConfig withMaxAttempts(int v) {
    return new ErpClientConfig(
        v,
        initialBackoff,
        maxBackoff,
        maxRetryAfter,
        callTimeout,
        rateLimitForPeriod,
        rateLimitPeriod,
        rateLimitTimeout,
        circuitWindowSize,
        circuitMinimumCalls,
        circuitFailureRate,
        circuitOpenDuration);
  }

  public ErpClientConfig withBackoff(Duration inicial, Duration maximo) {
    return new ErpClientConfig(
        maxAttempts,
        inicial,
        maximo,
        maxRetryAfter,
        callTimeout,
        rateLimitForPeriod,
        rateLimitPeriod,
        rateLimitTimeout,
        circuitWindowSize,
        circuitMinimumCalls,
        circuitFailureRate,
        circuitOpenDuration);
  }

  public ErpClientConfig withMaxRetryAfter(Duration v) {
    return new ErpClientConfig(
        maxAttempts,
        initialBackoff,
        maxBackoff,
        v,
        callTimeout,
        rateLimitForPeriod,
        rateLimitPeriod,
        rateLimitTimeout,
        circuitWindowSize,
        circuitMinimumCalls,
        circuitFailureRate,
        circuitOpenDuration);
  }

  public ErpClientConfig withCallTimeout(Duration v) {
    return new ErpClientConfig(
        maxAttempts,
        initialBackoff,
        maxBackoff,
        maxRetryAfter,
        v,
        rateLimitForPeriod,
        rateLimitPeriod,
        rateLimitTimeout,
        circuitWindowSize,
        circuitMinimumCalls,
        circuitFailureRate,
        circuitOpenDuration);
  }

  public ErpClientConfig withRateLimit(int chamadas, Duration periodo, Duration espera) {
    return new ErpClientConfig(
        maxAttempts,
        initialBackoff,
        maxBackoff,
        maxRetryAfter,
        callTimeout,
        chamadas,
        periodo,
        espera,
        circuitWindowSize,
        circuitMinimumCalls,
        circuitFailureRate,
        circuitOpenDuration);
  }

  public ErpClientConfig withCircuit(
      int janela, int minimoDeChamadas, float taxa, Duration aberto) {
    return new ErpClientConfig(
        maxAttempts,
        initialBackoff,
        maxBackoff,
        maxRetryAfter,
        callTimeout,
        rateLimitForPeriod,
        rateLimitPeriod,
        rateLimitTimeout,
        janela,
        minimoDeChamadas,
        taxa,
        aberto);
  }

  private static boolean positivo(Duration d) {
    return !d.isNegative() && !d.isZero();
  }

  private static void exigir(boolean condicao, String regra) {
    if (!condicao) {
      throw new IllegalArgumentException("ErpClientConfig inválido: " + regra);
    }
  }
}
