package com.recorra.common.qa;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.common.logging.MascaradorDePii;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** QA (REC-005): variações de query de rota OAuth que tentam escapar do mascaramento. */
class QaMascaradorQueryTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "code=abc123",
        "CODE=abc123",
        "Code=abc123&x=1",
        "co%64e=abc123",
        "x=1&code=abc123&code=abc124",
        "state=abc123&code=zzz999",
        "token=abc123",
        "access_token=abc123",
        "refresh_token=abc123",
        "client_secret=abc123",
        "code=abc%26state%3Dxyz",
        "code=",
        "a=1;code=abc123"
      })
  @DisplayName("Dado query OAuth com variações / Quando mascara / Então o valor sensível some")
  void valoresSensiveisSomem(String query) {
    String saida = MascaradorDePii.mascararQuery(query);

    assertThat(saida).doesNotContain("abc123", "abc124", "zzz999", "xyz");
  }

  @Test
  @DisplayName("Dado query com parâmetro comum / Quando mascara / Então o comum fica legível")
  void parametroComumSobrevive() {
    assertThat(MascaradorDePii.mascararQuery("pagina=2&code=abc123&ordem=asc"))
        .isEqualTo("pagina=2&code=***&ordem=asc");
  }

  @Test
  @DisplayName("Dado query nula ou vazia / Quando mascara / Então devolve como veio")
  void nuloEVazio() {
    assertThat(MascaradorDePii.mascararQuery(null)).isNull();
    assertThat(MascaradorDePii.mascararQuery("")).isEmpty();
    assertThat(MascaradorDePii.mascarar(null)).isNull();
  }

  @Test
  @DisplayName("Dado texto sem PII com caminho de classe / Quando mascara / Então fica intacto")
  void caminhoDeClasseIntacto() {
    String texto =
        "org/springframework/boot/autoconfigure/jdbc/DataSourceAutoConfiguration nada aqui";

    assertThat(MascaradorDePii.mascarar(texto)).isEqualTo(texto);
  }
}
