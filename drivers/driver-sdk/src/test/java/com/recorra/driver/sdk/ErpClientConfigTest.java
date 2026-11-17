package com.recorra.driver.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ErpClientConfigTest {

  private static final Duration D = Duration.ofMillis(10);

  @Test
  void padraoEValido() {
    ErpClientConfig c = ErpClientConfig.padrao();

    assertThat(c.maxAttempts()).isEqualTo(3);
    assertThat(c.rateLimitForPeriod()).isEqualTo(2);
    assertThat(c.callTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(c.maxRetryAfter()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  void withersTrocamSoOQueFoiPedido() {
    ErpClientConfig base = ErpClientConfig.padrao();

    ErpClientConfig c =
        base.withMaxAttempts(5)
            .withBackoff(D, Duration.ofSeconds(1))
            .withMaxRetryAfter(Duration.ofSeconds(7))
            .withCallTimeout(Duration.ofSeconds(2))
            .withRateLimit(9, Duration.ofSeconds(3), Duration.ZERO)
            .withCircuit(8, 4, 60f, Duration.ofSeconds(5));

    assertThat(c.maxAttempts()).isEqualTo(5);
    assertThat(c.initialBackoff()).isEqualTo(D);
    assertThat(c.maxBackoff()).isEqualTo(Duration.ofSeconds(1));
    assertThat(c.maxRetryAfter()).isEqualTo(Duration.ofSeconds(7));
    assertThat(c.callTimeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(c.rateLimitForPeriod()).isEqualTo(9);
    assertThat(c.rateLimitTimeout()).isEqualTo(Duration.ZERO);
    assertThat(c.circuitWindowSize()).isEqualTo(8);
    assertThat(c.circuitMinimumCalls()).isEqualTo(4);
    assertThat(c.circuitFailureRate()).isEqualTo(60f);
    assertThat(base.maxAttempts()).as("o original é imutável").isEqualTo(3);
  }

  @Test
  void rejeitaValoresInvalidos() {
    ErpClientConfig p = ErpClientConfig.padrao();

    assertThatThrownBy(() -> p.withMaxAttempts(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withBackoff(Duration.ZERO, D))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withBackoff(D, Duration.ofMillis(5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withCallTimeout(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withCallTimeout(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withMaxRetryAfter(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withCircuit(1, 1, 0f, D))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> p.withCircuit(1, 1, 101f, D))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(p.withCircuit(1, 1, 100f, D).circuitFailureRate()).isEqualTo(100f);
  }
}
