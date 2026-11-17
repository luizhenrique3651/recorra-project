package com.recorra.driver.bling;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.driver.sdk.ErpClientConfig;
import com.recorra.driver.sdk.IdempotencyKey;
import com.recorra.driver.sdk.ResilientErpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** Critério do REC-004: o driver-sdk é consumível pelo driver-bling. */
class DriverSdkConsumivelTest {

  @Test
  @DisplayName("Dado o driver-bling / Quando usa o driver-sdk / Então compila e funciona")
  void driverConsomeOSdk() {
    ResilientErpClient cliente =
        new ResilientErpClient("bling", WebClient.create(), ErpClientConfig.padrao());

    assertThat(cliente.circuitBreaker().getName()).isEqualTo("bling");
    assertThat(IdempotencyKey.paraVenda("c1", "bling", "42").valor()).isEqualTo("c1:bling:42");
  }
}
