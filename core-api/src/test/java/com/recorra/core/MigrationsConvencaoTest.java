package com.recorra.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.recorra.common.migration.ConvencaoDeMigrations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationsConvencaoTest {

  @Test
  @DisplayName(
      "Dado as migrations do core / Quando o CI valida / Então sem duplicata nem nome fora do padrão")
  void migrationsDoCoreSeguemAConvencao() {
    assertThat(ConvencaoDeMigrations.violacoes(ConvencaoDeMigrations.DIRETORIO_PADRAO)).isEmpty();
  }
}
