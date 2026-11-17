package com.recorra.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Gate do ADR-0001 item 3 (REC-003). Os dois primeiros testes são o que o CI executa de verdade; os
 * demais provam que o validador FALHA quando deve (schema inválido, fixture fora do schema).
 */
class ContratoValidacaoTest {

  private static final Path SCHEMAS = Path.of("src", "main", "schemas");
  private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

  @TempDir Path tmp;

  @Test
  @DisplayName("Dado os schemas do contrato / Quando o CI valida / Então todos cumprem o draft-07")
  void schemasDoContratoSaoValidos() {
    assertThat(new ValidadorDeContrato(SCHEMAS).validarSchemas()).isEmpty();
  }

  @Test
  @DisplayName(
      "Dado as fixtures / Quando o CI valida / Então as válidas passam e as inválidas falham")
  void fixturesDoContratoConferemComOsSchemas() {
    assertThat(new ValidadorDeContrato(SCHEMAS).validarFixtures(FIXTURES)).isEmpty();
  }

  @Test
  @DisplayName("Dado um schema inválido / Quando o CI valida / Então o job falha")
  void schemaInvalidoFalha() throws IOException {
    Path raiz = Files.createDirectories(tmp.resolve("schemas/modelo"));
    Files.writeString(
        raiz.resolve("ruim.schema.json"),
        "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"nao-existe\"}");
    Files.writeString(raiz.resolve("quebrado.schema.json"), "{ nao e json");

    assertThat(new ValidadorDeContrato(tmp.resolve("schemas")).validarSchemas())
        .anyMatch(e -> e.startsWith("modelo/ruim.schema.json"))
        .anyMatch(e -> e.contains("JSON ilegível"));
  }

  @Test
  @DisplayName("Dado uma fixture fora do schema / Quando o CI valida / Então o job falha")
  void fixtureForaDoSchemaFalha() throws IOException {
    Path schemas = Files.createDirectories(tmp.resolve("schemas/modelo"));
    Files.writeString(
        schemas.resolve("x.schema.json"),
        "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"object\","
            + "\"required\":[\"a\"]}");
    Path fixtures = Files.createDirectories(tmp.resolve("fixtures/modelo/x"));
    Files.writeString(fixtures.resolve("valida-sem-a.json"), "{}");
    Files.writeString(fixtures.resolve("invalida-com-a.json"), "{\"a\":1}");
    Files.writeString(fixtures.resolve("fixture.json"), "{\"a\":1}");
    Files.writeString(fixtures.resolve("invalida-ilegivel.json"), "{ nao e json");
    Files.createDirectories(tmp.resolve("fixtures/modelo/sem-schema"));
    Files.writeString(tmp.resolve("fixtures/modelo/sem-schema/valida-a.json"), "{}");

    assertThat(
            new ValidadorDeContrato(tmp.resolve("schemas"))
                .validarFixtures(tmp.resolve("fixtures")))
        .hasSize(4)
        .anyMatch(e -> e.contains("valida-sem-a.json") && e.contains("deveria ser válida"))
        .anyMatch(e -> e.contains("invalida-com-a.json") && e.contains("deveria ser inválida"))
        .anyMatch(e -> e.contains("fixture.json") && e.contains("valida- ou invalida-"))
        .anyMatch(e -> e.contains("sem-schema") && e.contains("não há schema"));
  }
}
