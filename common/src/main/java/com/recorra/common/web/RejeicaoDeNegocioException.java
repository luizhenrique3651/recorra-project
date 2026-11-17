package com.recorra.common.web;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Rejeição de regra de negócio (validação, conflito, não encontrado...): vira {@code WARN} SEM
 * stacktrace e um {@code ProblemDetail} 4xx. Não é um erro do sistema: nunca vira {@code ERROR}.
 *
 * <p>{@code detalhe} volta ao cliente na resposta; o log NUNCA o imprime (pode conter dado do
 * cliente), só {@code codigo} e o status.
 */
public class RejeicaoDeNegocioException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String codigo;

  public RejeicaoDeNegocioException(HttpStatus status, String codigo, String detalhe) {
    super(detalhe, null, false, false); // sem stacktrace: é fluxo normal de negócio
    this.status = Objects.requireNonNull(status, "status");
    this.codigo = Objects.requireNonNull(codigo, "codigo");
    if (!status.is4xxClientError()) {
      throw new IllegalArgumentException("rejeição de negócio é 4xx; recebeu " + status);
    }
  }

  public HttpStatus status() {
    return status;
  }

  /** Código estável da regra (ex.: {@code JANELA_INVALIDA}); seguro para log. */
  public String codigo() {
    return codigo;
  }
}
