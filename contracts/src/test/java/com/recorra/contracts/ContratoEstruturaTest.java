package com.recorra.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guarda as regras do ADR-0001 sobre os arquivos-fonte do contrato. */
class ContratoEstruturaTest {

  private static final Path MAIN = Path.of("src", "main");
  private static final List<Path> YAMLS =
      List.of(
          MAIN.resolve("asyncapi/recorra.asyncapi.yaml"),
          MAIN.resolve("openapi/ingestao.openapi.yaml"));
  private static final Pattern REF = Pattern.compile("\\$ref:\\s*'([^']+)'");

  @Test
  @DisplayName("Dado os YAML / Quando leio os $ref / Então todo schema referenciado existe")
  void asyncapiEOpenapiReferenciamSchemasQueExistem() throws IOException {
    for (Path yaml : YAMLS) {
      Matcher m = REF.matcher(Files.readString(yaml));
      int refsParaSchema = 0;
      while (m.find()) {
        String ref = m.group(1);
        if (ref.startsWith("#")) {
          continue; // referência interna do próprio documento
        }
        refsParaSchema++;
        assertThat(yaml.getParent().resolve(ref).normalize()).as(ref).exists();
      }
      assertThat(refsParaSchema).as(yaml.toString()).isPositive();
    }
  }

  @Test
  @DisplayName("Dado os YAML / Quando procuro definições / Então não redefinem formato")
  void asyncapiEOpenapiNaoRedefinemFormatos() throws IOException {
    for (Path yaml : YAMLS) {
      String texto = Files.readString(yaml);
      assertThat(texto)
          .as(yaml.toString())
          .doesNotContain("properties:")
          .doesNotContain("type: object");
    }
  }

  @Test
  @DisplayName(
      "Dado os schemas / Quando os leio / Então sem uniqueItems, additionalProperties e ERP")
  void schemasNaoUsamRecursosQueQuebramNoJackson3NemTrazemErp() throws IOException {
    try (Stream<Path> arquivos = Files.walk(MAIN.resolve("schemas"))) {
      List<Path> schemas = arquivos.filter(p -> p.toString().endsWith(".schema.json")).toList();
      assertThat(schemas).isNotEmpty();
      for (Path schema : schemas) {
        String texto = Files.readString(schema).toLowerCase(Locale.ROOT);
        assertThat(texto)
            .as(schema.toString())
            .doesNotContain("uniqueitems")
            .doesNotContain("additionalproperties")
            .doesNotContain("bling");
      }
    }
  }

  @Test
  @DisplayName("Dado o módulo / Quando listo o Java à mão / Então só existe package-info")
  void naoHaPojoEscritoAMaoNoModulo() throws IOException {
    try (Stream<Path> arquivos = Files.walk(MAIN.resolve("java"))) {
      List<String> java =
          arquivos
              .filter(p -> p.toString().endsWith(".java"))
              .map(p -> p.getFileName().toString())
              .toList();
      assertThat(java).containsExactly("package-info.java");
    }
  }
}
