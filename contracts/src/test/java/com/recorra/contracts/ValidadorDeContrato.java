package com.recorra.contracts;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validação do contrato (ADR-0001 item 3, REC-003): (1) cada {@code *.schema.json} contra o
 * meta-schema draft-07; (2) fixtures {@code valida-*.json} contra o schema da sua pasta (devem
 * passar) e {@code invalida-*.json} (devem falhar). Layout das fixtures: {@code
 * <raiz>/<caminho-do-schema-sem-.schema.json>/(valida|invalida)-*.json}.
 *
 * <p>É a mesma validação que o CI executa: ele só roda o {@code ./gradlew :contracts:test}.
 */
final class ValidadorDeContrato {

  /** Os {@code $id} dos schemas apontam para este prefixo; resolvemos para o diretório local. */
  private static final String PREFIXO_ID = "https://recorra.com/contracts/";

  private static final String META_SCHEMA_DRAFT_07 = "http://json-schema.org/draft-07/schema#";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Path schemas;
  private final SchemaRegistry registry;

  ValidadorDeContrato(Path schemas) {
    this.schemas = schemas;
    this.registry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_7,
            builder ->
                builder
                    .schemaRegistryConfig(
                        SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build())
                    .schemas(
                        iri -> {
                          if (!iri.startsWith(PREFIXO_ID)) {
                            return null;
                          }
                          Path arquivo = schemas.resolve(iri.substring(PREFIXO_ID.length()));
                          return Files.isRegularFile(arquivo) ? ler(arquivo) : null;
                        }));
  }

  /** Erros de meta-schema (ou JSON ilegível) de todos os schemas; vazio = ok. */
  List<String> validarSchemas() {
    List<String> erros = new ArrayList<>();
    Schema meta = registry.getSchema(SchemaLocation.of(META_SCHEMA_DRAFT_07));
    for (Path arquivo : arquivos(schemas, ".schema.json")) {
      try {
        for (Error e : meta.validate(JSON.readTree(ler(arquivo)))) {
          erros.add(schemas.relativize(arquivo) + ": " + e.getMessage());
        }
      } catch (RuntimeException e) {
        erros.add(schemas.relativize(arquivo) + ": JSON ilegível (" + e.getMessage() + ")");
      }
    }
    return erros;
  }

  /**
   * Erros das fixtures: {@code valida-*} que não passa, {@code invalida-*} que passa, nome fora do
   * padrão ou pasta sem schema correspondente. Vazio = ok.
   */
  List<String> validarFixtures(Path fixtures) {
    List<String> erros = new ArrayList<>();
    for (Path arquivo : arquivos(fixtures, ".json")) {
      Path pasta = fixtures.relativize(arquivo.getParent());
      Path schemaArquivo = schemas.resolve(pasta + ".schema.json");
      String nome = arquivo.getFileName().toString();
      String rotulo = fixtures.relativize(arquivo).toString();
      if (!Files.isRegularFile(schemaArquivo)) {
        erros.add(rotulo + ": não há schema em " + schemas.relativize(schemaArquivo));
        continue;
      }
      boolean esperaValida = nome.startsWith("valida-");
      if (!esperaValida && !nome.startsWith("invalida-")) {
        erros.add(rotulo + ": o nome deve começar com valida- ou invalida-");
        continue;
      }
      List<Error> falhas = validarContra(schemaArquivo, ler(arquivo));
      if (esperaValida && !falhas.isEmpty()) {
        erros.add(rotulo + ": deveria ser válida, mas: " + falhas.getFirst().getMessage());
      } else if (!esperaValida && falhas.isEmpty()) {
        erros.add(rotulo + ": deveria ser inválida, mas o schema a aceitou");
      }
    }
    return erros;
  }

  private List<Error> validarContra(Path schemaArquivo, String documento) {
    Schema schema =
        registry.getSchema(
            SchemaLocation.of(PREFIXO_ID + schemas.relativize(schemaArquivo).toString()));
    JsonNode no;
    try {
      no = JSON.readTree(documento);
    } catch (RuntimeException e) {
      return List.of(erroGenerico("JSON ilegível: " + e.getMessage()));
    }
    return schema.validate(no);
  }

  private static Error erroGenerico(String mensagem) {
    return Error.builder().messageKey("json").message(mensagem).build();
  }

  private static List<Path> arquivos(Path raiz, String sufixo) {
    try (Stream<Path> s = Files.walk(raiz)) {
      return s.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(sufixo))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String ler(Path arquivo) {
    try {
      return Files.readString(arquivo);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
