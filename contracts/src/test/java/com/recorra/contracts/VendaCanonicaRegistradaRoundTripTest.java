package com.recorra.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Round-trip do tipo GERADO a partir do schema, com o Jackson 3 (o mesmo do Spring Boot 4). */
class VendaCanonicaRegistradaRoundTripTest {

  private static final String JSON =
      """
      {
        "evento": "venda.canonica.registrada",
        "versao_contrato": "0.1.0",
        "comercio_id": "7d1b2f0e-3c4a-4c8e-9a51-0f6f2f1d9b11",
        "ocorrido_em": "2026-11-09T12:00:00Z",
        "venda": {
          "erp": "erp-exemplo",
          "erp_venda_id": "123",
          "data_venda": "2026-11-09T11:58:00Z",
          "valor_total": 99.90
        }
      }
      """;

  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  @DisplayName("Dado um evento / Quando serializo e desserializo / Então volta igual")
  void objetoSerializaEDesserializaIgual() {
    VendaCanonicaRegistrada evento = eventoDeExemplo();

    String json = mapper.writeValueAsString(evento);
    VendaCanonicaRegistrada lido = mapper.readValue(json, VendaCanonicaRegistrada.class);

    assertThat(lido).isEqualTo(evento).hasSameHashCodeAs(evento);
  }

  @Test
  @DisplayName("Dado o JSON do schema / Quando leio e reescrevo / Então fica equivalente")
  void jsonDoSchemaViraObjetoEVoltaEquivalente() {
    VendaCanonicaRegistrada lido = mapper.readValue(JSON, VendaCanonicaRegistrada.class);

    assertThat(lido.getEvento())
        .isEqualTo(VendaCanonicaRegistrada.Evento.VENDA_CANONICA_REGISTRADA);
    assertThat(lido.getComercioId())
        .isEqualTo(UUID.fromString("7d1b2f0e-3c4a-4c8e-9a51-0f6f2f1d9b11"));
    assertThat(lido.getVenda().getErpVendaId()).isEqualTo("123");
    assertThat(lido.getVenda().getValorTotal()).isEqualByComparingTo("99.90");

    JsonNode original = mapper.readTree(JSON);
    JsonNode reserializado = mapper.readTree(mapper.writeValueAsString(lido));
    assertThat(reserializado.get("evento")).isEqualTo(original.get("evento"));
    assertThat(reserializado.get("comercio_id")).isEqualTo(original.get("comercio_id"));
    assertThat(reserializado.get("venda").get("erp_venda_id"))
        .isEqualTo(original.get("venda").get("erp_venda_id"));
    assertThat(reserializado.get("venda").get("valor_total").decimalValue())
        .isEqualByComparingTo(original.get("venda").get("valor_total").decimalValue());
  }

  @Test
  @DisplayName("Dado um evento vazio / Quando serializo / Então não saem campos nulos")
  void camposOpcionaisAusentesNaoSaemNoJson() {
    VendaCanonicaRegistrada vazio = new VendaCanonicaRegistrada();

    assertThat(mapper.writeValueAsString(vazio)).isEqualTo("{}");
  }

  private static VendaCanonicaRegistrada eventoDeExemplo() {
    VendaCanonica venda = new VendaCanonica();
    venda.setErp("erp-exemplo");
    venda.setErpVendaId("123");
    venda.setDataVenda(OffsetDateTime.of(2026, 11, 9, 11, 58, 0, 0, ZoneOffset.UTC));
    venda.setValorTotal(new BigDecimal("99.90"));

    VendaCanonicaRegistrada evento = new VendaCanonicaRegistrada();
    evento.setEvento(VendaCanonicaRegistrada.Evento.VENDA_CANONICA_REGISTRADA);
    evento.setVersaoContrato("0.1.0");
    evento.setComercioId(UUID.fromString("7d1b2f0e-3c4a-4c8e-9a51-0f6f2f1d9b11"));
    evento.setOcorridoEm(OffsetDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneOffset.UTC));
    evento.setVenda(venda);
    return evento;
  }
}
