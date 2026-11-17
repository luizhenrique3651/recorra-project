package com.recorra.common.trace;

import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * W3C Trace Context ({@code traceparent}): {@code 00-<traceId 32 hex>-<spanId 16 hex>-<flags 2
 * hex>}. Só o formato e a geração; nada de exportação para backend de tracing (fora do escopo).
 */
public record Traceparent(String traceId, String spanId, String flags) {

  /** Nome do header HTTP/mensagem. */
  public static final String HEADER = "traceparent";

  private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
  private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");

  /** Dois dígitos hex: serve para flags e para a versão. */
  private static final Pattern FLAGS = Pattern.compile("[0-9a-f]{2}");

  private static final String SEM_ZEROS_32 = "0".repeat(32);
  private static final String SEM_ZEROS_16 = "0".repeat(16);

  public Traceparent {
    if (!valido(traceId, TRACE_ID, SEM_ZEROS_32)
        || !valido(spanId, SPAN_ID, SEM_ZEROS_16)
        || flags == null
        || !FLAGS.matcher(flags).matches()) {
      throw new IllegalArgumentException("traceparent inválido");
    }
  }

  /** Trace novo (job agendado, borda sem header de entrada). */
  public static Traceparent novo() {
    return new Traceparent(aleatorio(16), aleatorio(8), "01");
  }

  /** Lê um header; vazio se ausente ou malformado (a borda então começa um trace novo). */
  public static Optional<Traceparent> parse(String valor) {
    if (valor == null) {
      return Optional.empty();
    }
    String[] partes = valor.trim().split("-", -1);
    if (partes.length < 4 || !FLAGS.matcher(partes[0]).matches() || "ff".equals(partes[0])) {
      return Optional.empty();
    }
    if ("00".equals(partes[0]) && partes.length != 4) {
      return Optional.empty();
    }
    try {
      return Optional.of(new Traceparent(partes[1], partes[2], partes[3]));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Restaura de um valor guardado: o {@code traceparent} completo ou só o {@code traceId} (32 hex),
   * como na coluna do outbox (REC-042). Vazio se não for nenhum dos dois.
   */
  public static Optional<Traceparent> restaurar(String valor) {
    if (valor == null) {
      return Optional.empty();
    }
    String v = valor.trim();
    if (valido(v, TRACE_ID, SEM_ZEROS_32)) {
      return Optional.of(new Traceparent(v, aleatorio(8), "01"));
    }
    return parse(v);
  }

  /** Mesmo trace, novo span (a próxima chamada, ou o consumidor de um evento). */
  public Traceparent filho() {
    return new Traceparent(traceId, aleatorio(8), flags);
  }

  /** Valor do header: {@code 00-<traceId>-<spanId>-<flags>}. */
  public String toHeader() {
    return "00-" + traceId + "-" + spanId + "-" + flags;
  }

  private static boolean valido(String valor, Pattern formato, String zeros) {
    return valor != null && formato.matcher(valor).matches() && !zeros.equals(valor);
  }

  /**
   * Id aleatório em hex (16 ou 32 caracteres), nunca zero. {@code ThreadLocalRandom}: ids de trace
   * não são segredo, e {@code SecureRandom} sofre contenção/pinning de virtual threads por request.
   */
  private static String aleatorio(int bytes) {
    HexFormat hex = HexFormat.of();
    while (true) {
      long alto = ThreadLocalRandom.current().nextLong();
      long baixo = bytes == 16 ? ThreadLocalRandom.current().nextLong() : 0;
      if (alto == 0 && baixo == 0) {
        continue;
      }
      return bytes == 16 ? hex.toHexDigits(alto) + hex.toHexDigits(baixo) : hex.toHexDigits(alto);
    }
  }
}
