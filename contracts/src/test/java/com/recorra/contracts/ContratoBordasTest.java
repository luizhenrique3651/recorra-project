package com.recorra.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Bordas do tipo gerado: PII fora do toString, entradas inválidas e fidelidade dos valores. */
class ContratoBordasTest {

  private static final String ERP_VENDA_ID = "VENDA-SIGILOSA-987";
  private static final String COMERCIO_ID = "7d1b2f0e-3c4a-4c8e-9a51-0f6f2f1d9b11";

  private static final String JSON =
      """
      {
        "evento": "venda.canonica.registrada",
        "versao_contrato": "0.1.0",
        "comercio_id": "%s",
        "ocorrido_em": "2026-11-09T12:00:00Z",
        "venda": {
          "erp": "erp-exemplo",
          "erp_venda_id": "%s",
          "data_venda": "2026-11-09T08:58:00-03:00",
          "valor_total": 0.10
        }
      }
      """
          .formatted(COMERCIO_ID, ERP_VENDA_ID);

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  @DisplayName("Dado um evento com dados / Quando chamo toString / Então não imprime os campos")
  void toStringNaoVazaDadosDaVenda() {
    VendaCanonicaRegistrada evento = mapper.readValue(JSON, VendaCanonicaRegistrada.class);

    assertThat(evento.toString())
        .doesNotContain(COMERCIO_ID)
        .doesNotContain(ERP_VENDA_ID)
        .doesNotContain("erp-exemplo");
    assertThat(evento.getVenda().toString())
        .doesNotContain(ERP_VENDA_ID)
        .doesNotContain("erp-exemplo")
        .doesNotContain("0.1");
  }

  @Test
  @DisplayName("Dado um evento de outro tipo / Quando desserializo / Então rejeita o enum")
  void eventoDeOutroTipoERejeitado() {
    String json = JSON.replace("venda.canonica.registrada", "outro.evento");

    assertThatThrownBy(() -> mapper.readValue(json, VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("Dado JSON malformado ou vazio / Quando desserializo / Então rejeita")
  void jsonMalformadoERejeitado() {
    assertThatThrownBy(() -> mapper.readValue("{\"evento\":", VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("", VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(() -> mapper.readValue("[1,2]", VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("Dado tipos errados / Quando desserializo / Então rejeita uuid, data e número")
  void tiposInvalidosSaoRejeitados() {
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    JSON.replace(COMERCIO_ID, "nao-e-uuid"), VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    JSON.replace("2026-11-09T12:00:00Z", "ontem"), VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
    assertThatThrownBy(
            () ->
                mapper.readValue(JSON.replace("0.10", "\"muito\""), VendaCanonicaRegistrada.class))
        .isInstanceOf(JacksonException.class);
  }

  @Test
  @DisplayName("Dado valor monetário e data com offset / Quando faço round-trip / Então preserva")
  void valorEFusoSaoPreservados() {
    VendaCanonicaRegistrada lido = mapper.readValue(JSON, VendaCanonicaRegistrada.class);
    VendaCanonicaRegistrada volta =
        mapper.readValue(mapper.writeValueAsString(lido), VendaCanonicaRegistrada.class);

    BigDecimal valor = volta.getVenda().getValorTotal();
    assertThat(valor).isEqualByComparingTo("0.10");
    assertThat(valor).isEqualTo(new BigDecimal("0.10"));
    OffsetDateTime data = volta.getVenda().getDataVenda();
    assertThat(data.toInstant())
        .isEqualTo(OffsetDateTime.parse("2026-11-09T11:58:00Z").toInstant());
  }

  @Test
  @DisplayName("Dado campos desconhecidos / Quando leio com mapper tolerante / Então ignora")
  void leitorToleranteIgnoraCamposDesconhecidosSemEcoar() {
    JsonMapper tolerante =
        JsonMapper.builder()
            .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    String json = JSON.replace("\"venda\":", "\"campo_do_futuro\": {\"x\": 1}, \"venda\":");

    VendaCanonicaRegistrada lido = tolerante.readValue(json, VendaCanonicaRegistrada.class);

    assertThat(lido.getVenda().getErpVendaId()).isEqualTo(ERP_VENDA_ID);
    assertThat(tolerante.writeValueAsString(lido)).doesNotContain("campo_do_futuro");
  }
}
