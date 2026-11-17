package com.recorra.common.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConvencaoDeMigrationsTest {

  @TempDir Path dir;

  private void cria(String... nomes) throws IOException {
    for (String nome : nomes) {
      Files.writeString(dir.resolve(nome), "select 1;");
    }
  }

  @Test
  @DisplayName("Dado versões distintas no padrão / Quando valida / Então sem violação")
  void versoesDistintasPassam() throws IOException {
    cria("V0001__baseline.sql", "V0301__cria_tabela.sql", "V0302__indice.sql");

    assertThat(ConvencaoDeMigrations.violacoes(dir)).isEmpty();
  }

  @Test
  @DisplayName("Dado duas migrations com a mesma versão / Quando valida / Então falha")
  void versaoDuplicadaFalha() throws IOException {
    cria("V0301__cria_tabela.sql", "V0301__outra_coisa.sql");

    assertThat(ConvencaoDeMigrations.violacoes(dir))
        .singleElement()
        .asString()
        .contains("V0301")
        .contains("duplicada");
  }

  @Test
  @DisplayName("Dado nomes fora da convenção / Quando valida / Então cada um é violação")
  void nomeForaDaConvencaoFalha() throws IOException {
    cria("V1__curto.sql", "V0101_um_underscore.sql", "V0102__Maiuscula.sql", "V010203__longo.sql");

    assertThat(ConvencaoDeMigrations.violacoes(dir)).hasSize(4);
  }

  @Test
  @DisplayName("Dado arquivo que não é .sql / Quando valida / Então é ignorado")
  void ignoraOutrosArquivos() throws IOException {
    cria("V0001__baseline.sql", "README.md");

    assertThat(ConvencaoDeMigrations.violacoes(dir)).isEmpty();
  }

  @Test
  @DisplayName("Dado diretório inexistente / Quando valida / Então é violação")
  void diretorioInexistenteFalha() {
    assertThat(ConvencaoDeMigrations.violacoes(dir.resolve("nao-existe"))).hasSize(1);
  }

  @Test
  @DisplayName("Dado três migrations com a mesma versão / Quando valida / Então 2 violações")
  void triplicataGeraDuasViolacoes() throws IOException {
    cria("V0301__a.sql", "V0301__b.sql", "V0301__c.sql", "V0001__baseline.sql");

    assertThat(ConvencaoDeMigrations.violacoes(dir)).hasSize(2).allMatch(v -> v.contains("V0301"));
  }

  @Test
  @DisplayName("Dado extensão .SQL maiúscula ou sufixo extra / Quando valida / Então ignora/aceita")
  void extensaoMaiusculaNaoEhMigrationParaOFlyway() throws IOException {
    // O Flyway só lê *.sql; um .SQL ou .sql.bak jamais roda, logo não deve contar como migration.
    cria("V0001__baseline.sql", "V0001__outra.SQL", "V0001__backup.sql.bak");

    assertThat(ConvencaoDeMigrations.violacoes(dir)).isEmpty();
  }

  @Test
  @DisplayName("Dado subdiretório com nome .sql / Quando valida / Então não é migration")
  void diretorioNaoContaComoMigration() throws IOException {
    cria("V0001__baseline.sql");
    Files.createDirectory(dir.resolve("V0001__pasta.sql"));

    assertThat(ConvencaoDeMigrations.violacoes(dir)).isEmpty();
  }

  @Test
  @DisplayName("Dado versão V0000 ou prefixo minúsculo / Quando valida / Então segue o padrão")
  void prefixoMinusculoEhViolacao() throws IOException {
    cria("v0301__minusculo.sql", "R__repetivel.sql");

    assertThat(ConvencaoDeMigrations.violacoes(dir)).hasSize(2);
  }
}
