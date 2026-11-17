package com.recorra.common.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Convenção de migrations Flyway do Recorra (REC-003): {@code V<sprint><seq>__descricao.sql},
 * sprint e sequência com 2 dígitos (S3 → {@code V0301}; baseline = {@code V0001}), numerada pela
 * ordem de execução. Cada app (core-api, driver-bling...) tem a sua sequência. {@code outOfOrder} é
 * proibido, então uma versão duplicada ou fora da convenção precisa quebrar o build.
 */
public final class ConvencaoDeMigrations {

  /** Local padrão das migrations dentro de um módulo. */
  public static final Path DIRETORIO_PADRAO =
      Path.of("src", "main", "resources", "db", "migration");

  private static final Pattern NOME = Pattern.compile("^V(\\d{2})(\\d{2})__[a-z0-9_]+\\.sql$");

  private ConvencaoDeMigrations() {}

  /** Violações da convenção no diretório; lista vazia = ok. Diretório inexistente é violação. */
  public static List<String> violacoes(Path diretorio) {
    if (!Files.isDirectory(diretorio)) {
      return List.of("diretório de migrations inexistente: " + diretorio);
    }
    List<String> violacoes = new ArrayList<>();
    Map<String, String> porVersao = new HashMap<>();
    for (String arquivo : nomesSql(diretorio)) {
      Matcher m = NOME.matcher(arquivo);
      if (!m.matches()) {
        violacoes.add(
            "fora da convenção V<sprint 2 dígitos><seq 2 dígitos>__descricao_minuscula.sql: "
                + arquivo);
        continue;
      }
      String versao = m.group(1) + m.group(2);
      String anterior = porVersao.putIfAbsent(versao, arquivo);
      if (anterior != null) {
        violacoes.add("versão V" + versao + " duplicada: " + anterior + " e " + arquivo);
      }
    }
    return violacoes;
  }

  private static List<String> nomesSql(Path diretorio) {
    try (Stream<Path> arquivos = Files.list(diretorio)) {
      return arquivos
          .filter(Files::isRegularFile)
          .map(p -> p.getFileName().toString())
          .filter(n -> n.endsWith(".sql"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
