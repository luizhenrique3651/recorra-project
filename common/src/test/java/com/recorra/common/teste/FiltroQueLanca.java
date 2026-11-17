package com.recorra.common.teste;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Filtro interno que lança: exceção fora do MVC, onde o handler global não alcança. */
@Component
class FiltroQueLanca extends OncePerRequestFilter implements Ordered {

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getRequestURI().equals("/teste/filtro-lanca")) {
      throw new IllegalStateException("falha de filtro com dado interno: jdbc:postgresql://x");
    }
    chain.doFilter(request, response);
  }
}
