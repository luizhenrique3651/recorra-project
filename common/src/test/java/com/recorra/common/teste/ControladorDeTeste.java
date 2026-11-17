package com.recorra.common.teste;

import com.recorra.common.web.RejeicaoDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ControladorDeTeste {

  private static final Logger LOG = LoggerFactory.getLogger(ControladorDeTeste.class);

  @GetMapping("/teste/ok")
  String ok() {
    LOG.info("dentro do handler");
    return "ok";
  }

  @GetMapping("/teste/negocio")
  String negocio() {
    throw new RejeicaoDeNegocioException(
        HttpStatus.UNPROCESSABLE_CONTENT, "JANELA_INVALIDA", "A janela precisa ser maior que zero");
  }

  @GetMapping("/teste/falha")
  String falha() {
    throw new IllegalStateException(
        "detalhe interno secreto: jdbc:postgresql://db-interno/recorra");
  }

  @PostMapping("/teste/corpo")
  String corpo(@RequestBody String corpo) {
    return "recebido " + corpo.length();
  }

  @GetMapping("/teste/pii")
  String pii() {
    LOG.info(
        "cliente nome=João da Silva telefone=+55 (11) 98765-4321 cpf=123.456.789-09"
            + " email=joao.silva@example.com access_token=tok-super-secreto");
    return "ok";
  }

  @GetMapping("/clientes/{id}")
  String cliente(@PathVariable String id) {
    return "cliente";
  }

  @GetMapping("/driver/v1/bling/callback")
  String callback() {
    return "ok";
  }
}
