package com.recorra.driver.sdk;

/**
 * Publica eventos canônicos para o core. É só uma porta: o <b>transporte é plugável</b> (no MVP é o
 * HTTP de ingestão idempotente do REC-043; trocar por fila/Kafka é trocar a implementação). O SDK
 * não traz transporte nenhum.
 *
 * <p>Ponto de extensão para a propagação do {@code traceparent} (REC-005): decorar esta interface
 * com {@link DelegatingCanonicalPublisher}. Nada de tracing é implementado aqui.
 */
public interface CanonicalPublisher {

  /**
   * Publica o evento: SOMENTE tipos de {@code com.recorra.contracts} (os gerados do schema). A
   * mesma {@code chave} repetida resulta em {@link PublishResult#DUPLICATE}, nunca em duplicação no
   * core.
   *
   * @throws RuntimeException se o transporte falhar; quem chama decide reenviar (a chave garante a
   *     idempotência)
   */
  PublishResult publish(Object evento, IdempotencyKey chave);

  default PublishResult publish(CanonicalMessage mensagem) {
    return publish(mensagem.evento(), mensagem.chave());
  }
}
