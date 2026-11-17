package com.recorra.driver.sdk;

/** Resultado do tratamento de um webhook. */
public enum WebhookOutcome {
  /** Assinatura válida e eventos publicados. */
  ACCEPTED,
  /** Assinatura inválida: nada foi lido nem publicado. */
  REJECTED
}
