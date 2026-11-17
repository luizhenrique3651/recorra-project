package com.recorra.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.Set;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * Mascaramento no appender: aplica o {@link MascaradorDePii} a TODO valor de texto do JSON de log
 * (mensagem, stacktrace, MDC, nome do logger). Ligado por {@code
 * logging.structured.json.customizer} (veja {@code observabilidade-common.yml}).
 */
public final class MascaramentoJsonCustomizer
    implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

  /**
   * Campos que o mascarador nunca toca (correlação de logs). Elemento de array (ex.: {@code tags})
   * tem {@code path.name() == null}: esse PASSA pelo mascaramento (e {@code Set.of.contains(null)}
   * lança NPE, o que faria o Logback perder o evento).
   */
  private static final Set<String> INTOCAVEIS = Set.of("traceId", "spanId", "traceFlags");

  @Override
  public void customize(JsonWriter.Members<ILoggingEvent> members) {
    // traceId/spanId/traceFlags são hex validado e a CHAVE da correlação: nunca alterados.
    members.applyingValueProcessor(
        JsonWriter.ValueProcessor.of(String.class, MascaradorDePii::mascarar)
            .whenHasPath(path -> path.name() == null || !INTOCAVEIS.contains(path.name())));
  }
}
