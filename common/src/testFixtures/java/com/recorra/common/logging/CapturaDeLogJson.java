package com.recorra.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Teste de appender: acopla ao root um appender com o MESMO encoder JSON do console (o do Boot, já
 * com o mascaramento ligado por {@code logging.structured.json.customizer}) e devolve o que foi
 * escrito. Use em {@code try-with-resources}.
 */
public final class CapturaDeLogJson implements AutoCloseable {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final Logger root;
  private final OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
  private final ByteArrayOutputStream saida = new ByteArrayOutputStream();

  public CapturaDeLogJson() {
    LoggerContext contexto = (LoggerContext) LoggerFactory.getILoggerFactory();
    StructuredLogEncoder encoder = new StructuredLogEncoder();
    encoder.setContext(contexto);
    encoder.setFormat("logstash");
    encoder.start();
    appender.setContext(contexto);
    appender.setEncoder(encoder);
    appender.setOutputStream(saida);
    appender.start();
    root = contexto.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(appender);
  }

  /** Tudo que foi escrito (texto bruto, uma linha JSON por evento). */
  public String texto() {
    return saida.toString(StandardCharsets.UTF_8);
  }

  /** Cada linha como mapa (chaves do logstash: message, level, traceId, stack_trace...). */
  @SuppressWarnings("unchecked") // o logstash escreve um objeto JSON por linha
  public List<Map<String, Object>> eventos() {
    return texto()
        .lines()
        .filter(l -> l.startsWith("{"))
        .map(l -> (Map<String, Object>) JSON.readValue(l, Map.class))
        .toList();
  }

  public List<Map<String, Object>> eventos(String nivel) {
    return eventos().stream().filter(e -> nivel.equals(e.get("level"))).toList();
  }

  @Override
  public void close() {
    root.detachAppender(appender);
    appender.stop();
  }
}
