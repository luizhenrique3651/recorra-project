package com.recorra.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RejeicaoDeNegocioExceptionTest {

  @Test
  void naoCarregaStacktrace() {
    RejeicaoDeNegocioException e =
        new RejeicaoDeNegocioException(HttpStatus.CONFLICT, "DUPLICADO", "já existe");

    assertThat(e.getStackTrace()).isEmpty();
    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(e.codigo()).isEqualTo("DUPLICADO");
  }

  @Test
  void soAceita4xx() {
    assertThatThrownBy(
            () -> new RejeicaoDeNegocioException(HttpStatus.INTERNAL_SERVER_ERROR, "X", "y"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
