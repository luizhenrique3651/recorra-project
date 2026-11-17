package com.recorra.driver.sdk;

import java.util.Objects;

/**
 * Chave de idempotência de um evento canônico: {@code <comercio_id>:<erp>:<erp_venda_id>}.
 *
 * <p><b>Invariante (roadmap §1.6, item 3):</b> a idempotência é por <i>comércio</i> + ERP + id da
 * venda no ERP. O mesmo {@code erp_venda_id} em comércios diferentes são vendas diferentes e NÃO
 * podem colidir (isso furaria o isolamento multi-tenant). A mesma venda do mesmo comércio sempre
 * gera a mesma chave, então um reenvio (retry, webhook repetido, repoll) é reconhecido como
 * duplicata pelo core. Os valores são opacos para o SDK, que não conhece ERP nenhum.
 */
public record IdempotencyKey(String valor) {

  private static final char SEPARADOR = ':';

  public IdempotencyKey {
    Objects.requireNonNull(valor, "valor");
    if (valor.isBlank()) {
      throw new IllegalArgumentException("a chave de idempotência não pode ser vazia");
    }
  }

  /**
   * Chave de uma venda.
   *
   * @param comercioId o comércio dono da venda (sem {@code :})
   * @param erp código do ERP de origem (sem {@code :})
   * @param erpVendaId identificador da venda no ERP (pode conter {@code :}: é o último campo)
   */
  public static IdempotencyKey paraVenda(String comercioId, String erp, String erpVendaId) {
    exigirPreenchido(comercioId, "comercioId");
    exigirPreenchido(erp, "erp");
    exigirPreenchido(erpVendaId, "erpVendaId");
    semSeparador(comercioId, "comercioId");
    semSeparador(erp, "erp");
    return new IdempotencyKey(comercioId + SEPARADOR + erp + SEPARADOR + erpVendaId);
  }

  private static void exigirPreenchido(String parte, String nome) {
    Objects.requireNonNull(parte, nome);
    if (parte.isBlank()) {
      throw new IllegalArgumentException(nome + " não pode ser vazio");
    }
  }

  private static void semSeparador(String parte, String nome) {
    if (parte.indexOf(SEPARADOR) >= 0) {
      // Sem isso ("a:b","c") e ("a","b:c") colidiriam na mesma chave.
      throw new IllegalArgumentException(nome + " não pode conter '" + SEPARADOR + "'");
    }
  }
}
