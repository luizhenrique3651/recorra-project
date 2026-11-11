package com.recorra.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.contracts.VendaCanonica;
import com.recorra.contracts.VendaCanonicaRegistrada;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

/** O core compila e roda contra o tipo gerado, com o ObjectMapper configurado pelo Boot 4. */
@SpringBootTest
class ContratoCanonicoRoundTripTest {

  @Autowired ObjectMapper mapper;

  @Test
  void tipoGeradoFazRoundTripComOObjectMapperDoBoot() {
    VendaCanonica venda = new VendaCanonica();
    venda.setErp("erp-exemplo");
    venda.setErpVendaId("123");
    venda.setDataVenda(OffsetDateTime.of(2026, 11, 9, 11, 58, 0, 0, ZoneOffset.UTC));
    venda.setValorTotal(new BigDecimal("99.90"));
    VendaCanonicaRegistrada evento = new VendaCanonicaRegistrada();
    evento.setEvento(VendaCanonicaRegistrada.Evento.VENDA_CANONICA_REGISTRADA);
    evento.setVersaoContrato("0.1.0");
    evento.setComercioId(UUID.randomUUID());
    evento.setOcorridoEm(OffsetDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneOffset.UTC));
    evento.setVenda(venda);

    VendaCanonicaRegistrada lido =
        mapper.readValue(mapper.writeValueAsString(evento), VendaCanonicaRegistrada.class);

    assertThat(lido).isEqualTo(evento);
  }

  @Test
  void leitorToleranteIgnoraCampoDesconhecido() {
    String json =
        """
        {"evento":"venda.canonica.registrada","campo_novo_do_futuro":1,
         "venda":{"erp":"e","erp_venda_id":"1","campo_novo":true}}
        """;

    VendaCanonicaRegistrada lido = mapper.readValue(json, VendaCanonicaRegistrada.class);

    assertThat(lido.getVenda().getErpVendaId()).isEqualTo("1");
  }
}
