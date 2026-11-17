package com.recorra.common.web;

import com.recorra.common.trace.MdcEscopo;
import com.recorra.common.trace.TraceContext;
import com.recorra.common.trace.Traceparent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro mais externo: entrada do W3C {@code traceparent} e última rede de segurança.
 *
 * <ul>
 *   <li>Com header válido mantém o {@code traceId} (novo span); sem header (ou malformado) começa
 *       um trace novo. O contexto vai para o MDC, volta no header {@code traceparent} da resposta
 *       (inclusive nas de erro) e é removido em {@code finally}.
 *   <li>Exceção que escapa da cadeia (fora do MVC, onde o handler global não alcança) é capturada
 *       DENTRO do escopo do MDC: um único {@code ERROR} com stacktrace e {@code traceId}, resposta
 *       500 {@code ProblemDetail} genérica com o {@code traceId} (se ainda não commitada), e não
 *       relança (sem segundo ERROR do Tomcat).
 * </ul>
 */
public class TraceparentFilter extends OncePerRequestFilter implements Ordered {

  private static final Logger LOG = LoggerFactory.getLogger(TraceparentFilter.class);

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  @SuppressWarnings("try") // o escopo só existe para fechar (limpar o MDC) em finally
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Traceparent trace =
        Traceparent.parse(request.getHeader(Traceparent.HEADER))
            .map(Traceparent::filho)
            .orElseGet(Traceparent::novo);
    try (MdcEscopo escopo = TraceContext.abrir(trace)) {
      response.setHeader(Traceparent.HEADER, trace.toHeader());
      try {
        chain.doFilter(request, response);
      } catch (ServletException | RuntimeException e) {
        LOG.error("exceção não tratada na cadeia de filtros", e); // o único ERROR desta falha
        responder500(response, trace.traceId());
      }
    }
  }

  private static void responder500(HttpServletResponse response, String traceId)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.resetBuffer();
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // traceId é hex (seguro para JSON); nada da exceção vai para o cliente.
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Erro interno\",\"status\":500,"
                + "\"detail\":\"Ocorreu um erro inesperado. Informe o traceId ao suporte.\","
                + "\"traceId\":\""
                + traceId
                + "\"}");
  }
}
