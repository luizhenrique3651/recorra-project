package com.recorra.driver.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

  @Test
  @DisplayName("Dado a mesma venda do ERP / Quando gero a chave duas vezes / Então é igual")
  void mesmaVendaMesmaChave() {
    assertThat(IdempotencyKey.paraVenda("c1", "erp-x", "123"))
        .isEqualTo(IdempotencyKey.paraVenda("c1", "erp-x", "123"))
        .hasToString("IdempotencyKey[valor=c1:erp-x:123]");
    assertThat(IdempotencyKey.paraVenda("c1", "erp-x", "123").valor()).isEqualTo("c1:erp-x:123");
  }

  @Test
  @DisplayName(
      "Dado o mesmo erp_venda_id em comércios diferentes / Quando gero / Então as chaves diferem")
  void mesmoIdEmComerciosDiferentesNaoColide() {
    assertThat(IdempotencyKey.paraVenda("comercio-a", "erp-x", "123"))
        .isNotEqualTo(IdempotencyKey.paraVenda("comercio-b", "erp-x", "123"));
  }

  @Test
  @DisplayName("Dado vendas ou ERPs diferentes / Quando gero / Então as chaves diferem")
  void vendasDiferentesChavesDiferentes() {
    assertThat(IdempotencyKey.paraVenda("c1", "erp-x", "1"))
        .isNotEqualTo(IdempotencyKey.paraVenda("c1", "erp-x", "2"))
        .isNotEqualTo(IdempotencyKey.paraVenda("c1", "erp-y", "1"));
  }

  @Test
  @DisplayName("Dado separador no id da venda / Quando gero / Então aceita (é o último campo)")
  void idDaVendaPodeConterSeparador() {
    assertThat(IdempotencyKey.paraVenda("c1", "erp", "a:b").valor()).isEqualTo("c1:erp:a:b");
  }

  @Test
  void rejeitaPartesInvalidas() {
    assertThatThrownBy(() -> IdempotencyKey.paraVenda("c1", " ", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdempotencyKey.paraVenda("c1", "erp", ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdempotencyKey.paraVenda("", "erp", "1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdempotencyKey.paraVenda("c1", null, "1"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> IdempotencyKey.paraVenda(null, "erp", "1"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> IdempotencyKey.paraVenda("c1", "a:b", "c"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdempotencyKey.paraVenda("c:1", "erp", "c"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IdempotencyKey(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new IdempotencyKey(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("Dado combinações adversárias de partes / Quando gero / Então a chave é injetiva")
  void chaveEInjetivaParaPartesValidas() {
    String[] comercios = {"a", "b", "ab", "a-b", "A"};
    String[] erps = {"x", "y", "xy", "x-y", "X"};
    String[] vendas = {"1", "2", "12", "1:2", "x:1", "", "a:b:c"};
    java.util.Map<String, String> vistos = new java.util.HashMap<>();
    for (String c : comercios) {
      for (String e : erps) {
        for (String v : vendas) {
          if (v.isEmpty()) {
            continue;
          }
          String chave = IdempotencyKey.paraVenda(c, e, v).valor();
          String anterior = vistos.put(chave, c + "|" + e + "|" + v);
          assertThat(anterior).as("colisão de " + chave).isNull();
        }
      }
    }
  }
}
