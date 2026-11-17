package com.recorra.common.web;

import com.recorra.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Handler global RFC 7807 ({@code ProblemDetail}) com o {@code traceId} na resposta.
 *
 * <p>Política de níveis (REC-005): rejeição de negócio e erro de requisição (4xx) = {@code WARN}
 * sem stacktrace e sem a mensagem da exceção; erro inesperado = UM {@code ERROR} com stacktrace,
 * aqui na borda, e resposta 500 genérica (nenhum detalhe interno). Nunca loga body.
 */
@RestControllerAdvice
public class ProblemaGlobalHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemaGlobalHandler.class);

  @ExceptionHandler(RejeicaoDeNegocioException.class)
  public ResponseEntity<Object> rejeicaoDeNegocio(
      RejeicaoDeNegocioException e, WebRequest request) {
    LOG.warn("rejeição de negócio: codigo={} status={}", e.codigo(), e.status().value());
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    problema.setTitle("Regra de negócio violada");
    problema.setProperty("codigo", e.codigo());
    return ResponseEntity.status(e.status()).body(comTraceId(problema));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> inesperado(Exception e) {
    LOG.error("erro inesperado", e); // o único ERROR com stacktrace: na borda
    ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problema.setTitle("Erro interno");
    problema.setDetail("Ocorreu um erro inesperado. Informe o traceId ao suporte.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(comTraceId(problema));
  }

  /** Erros de requisição do próprio Spring MVC (400, 404, 405, 415...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (status.is5xxServerError()) {
      LOG.error("erro de infraestrutura HTTP {}", status.value(), ex);
    } else {
      LOG.warn(
          "requisição rejeitada: status={} causa={}",
          status.value(),
          ex.getClass().getSimpleName());
    }
    ResponseEntity<Object> resposta =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (resposta != null && resposta.getBody() instanceof ProblemDetail problema) {
      comTraceId(problema);
    }
    return resposta;
  }

  private static ProblemDetail comTraceId(ProblemDetail problema) {
    problema.setProperty("traceId", TraceContext.traceIdAtual());
    return problema;
  }
}
