package com.recorra.driver.sdk;

import java.util.Objects;

/**
 * Evento canônico pronto para publicar: o objeto gerado do contrato ({@code com.recorra.contracts})
 * e a chave de idempotência dele.
 */
public record CanonicalMessage(Object evento, IdempotencyKey chave) {

  public CanonicalMessage {
    Objects.requireNonNull(evento, "evento");
    Objects.requireNonNull(chave, "chave");
  }

  /** Sem {@code evento} de propósito: pode carregar PII (nunca em log). */
  @Override
  public String toString() {
    return "CanonicalMessage[chave=" + chave + "]";
  }
}
