package com.recorra.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Liga a base de logs (JSON + mascaramento de PII) em QUALQUER app que tenha o {@code common} no
 * classpath, sem import manual: um app novo não pode subir logando PII em claro. O {@code
 * observabilidade-common.yml} entra como property source de BAIXA precedência (o app pode
 * sobrescrever uma chave, mas não esquece de ligá-la). Roda antes da inicialização do logging.
 */
public class ObservabilidadeEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String ARQUIVO = "observabilidade-common.yml";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Resource recurso = new ClassPathResource(ARQUIVO);
    try {
      List<PropertySource<?>> fontes =
          new YamlPropertySourceLoader().load("observabilidade-common", recurso);
      fontes.forEach(environment.getPropertySources()::addLast);
    } catch (IOException e) {
      throw new UncheckedIOException("não foi possível ler " + ARQUIVO, e);
    }
  }
}
