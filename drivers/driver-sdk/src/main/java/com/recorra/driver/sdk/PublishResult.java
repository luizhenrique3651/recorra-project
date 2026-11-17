package com.recorra.driver.sdk;

/** Resultado de uma publicação canônica. */
public enum PublishResult {
  /** O core aceitou o evento. */
  ACCEPTED,
  /** O core já tinha este evento (mesma {@link IdempotencyKey}); nada foi duplicado. */
  DUPLICATE
}
