package com.recorra.common.trace;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Escopo de MDC: grava chaves ao abrir e, ao fechar, devolve cada uma ao valor anterior (ou a
 * remove). Use em {@code try-with-resources}: o MDC é limpo mesmo em exceção, e escopos aninhados
 * não apagam o contexto do de fora.
 */
public final class MdcEscopo implements AutoCloseable {

  private final Map<String, String> anteriores = new LinkedHashMap<>();

  MdcEscopo(Map<String, String> novos) {
    novos.forEach(
        (chave, valor) -> {
          anteriores.put(chave, MDC.get(chave));
          if (valor == null) {
            MDC.remove(chave);
          } else {
            MDC.put(chave, valor);
          }
        });
  }

  @Override
  public void close() {
    anteriores.forEach(
        (chave, anterior) -> {
          if (anterior == null) {
            MDC.remove(chave);
          } else {
            MDC.put(chave, anterior);
          }
        });
  }
}
