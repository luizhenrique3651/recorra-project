package com.recorra.common;

import com.recorra.common.logging.ReactorErroDescartado;
import com.recorra.common.web.ProblemaGlobalHandler;
import com.recorra.common.web.RequestLogFilter;
import com.recorra.common.web.TraceparentFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Liga a observabilidade (REC-005) em todo app servlet que tenha o {@code common}: filtro do {@code
 * traceparent}, log de request e handler global RFC 7807.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservabilidadeAutoConfiguration {

  /** Política de erros descartados do Reactor, só se o Reactor está no app. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "reactor.core.publisher.Hooks")
  static class ReactorConfig {
    ReactorConfig() {
      ReactorErroDescartado.instalar();
    }
  }

  @Bean
  @ConditionalOnMissingBean
  TraceparentFilter traceparentFilter() {
    return new TraceparentFilter();
  }

  @Bean
  @ConditionalOnMissingBean
  RequestLogFilter requestLogFilter() {
    return new RequestLogFilter();
  }

  @Bean
  @ConditionalOnMissingBean
  ProblemaGlobalHandler problemaGlobalHandler() {
    return new ProblemaGlobalHandler();
  }
}
