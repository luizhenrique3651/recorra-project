package com.recorra.common.web;

import com.recorra.common.logging.MascaradorDePii;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Log de request: UMA linha por chamada com método, rota, query mascarada, status e duração.
 *
 * <p>Nunca loga body nem cabeçalhos. A rota sai pelo padrão ({@code /clientes/{id}}), não pelo path
 * real (que pode carregar identificador de cliente); só sem rota mapeada cai no path, mascarado. Os
 * parâmetros {@code code}, {@code state} e {@code token} (rotas OAuth) saem como {@code ***}.
 * Actuator fica de fora. O access log do Tomcat fica DESLIGADO ({@code
 * server.tomcat.accesslog.enabled=false}): ligá-lo exige um pattern sem a query.
 */
public class RequestLogFilter extends OncePerRequestFilter implements Ordered {

  private static final Logger LOG = LoggerFactory.getLogger(RequestLogFilter.class);

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10; // depois do TraceparentFilter: o log já tem traceId
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long inicio = System.nanoTime();
    Exception falha = null;
    try {
      chain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException e) {
      falha = e;
      throw e;
    } finally {
      if (LOG.isInfoEnabled()) {
        logar(request, response, falha, (System.nanoTime() - inicio) / 1_000_000);
      }
    }
  }

  private static void logar(
      HttpServletRequest request, HttpServletResponse response, Exception falha, long ms) {
    String query = MascaradorDePii.mascararQuery(request.getQueryString());
    // Cadeia que lançou: a resposta ainda diz 200, mas vai virar 500; só a CLASSE da exceção.
    int status = falha == null ? response.getStatus() : 500;
    LOG.info(
        "request {} {}{} status={} duracaoMs={}{}",
        request.getMethod(),
        rota(request),
        query == null || query.isEmpty() ? "" : "?" + query,
        status,
        ms,
        falha == null ? "" : " erro=" + falha.getClass().getSimpleName());
  }

  private static String rota(HttpServletRequest request) {
    Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return padrao != null ? padrao.toString() : MascaradorDePii.mascarar(request.getRequestURI());
  }
}
