package com.recorra.common.logging;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Hooks;

/**
 * Política de "um único ERROR na borda" para o Reactor: por padrão, um erro descartado (chegou
 * depois do cancelamento ou do término do fluxo) vira {@code ERROR} com stacktrace em {@code
 * reactor.core.publisher.Operators}, ex.: o {@code CancellationException "Request cancelled"} do
 * conector JDK depois de um timeout. Aqui:
 *
 * <ul>
 *   <li>descartado por cancelamento: {@code DEBUG}, sem stacktrace;
 *   <li>qualquer outro descartado: {@code WARN} só com a CLASSE (sem mensagem nem stacktrace).
 * </ul>
 *
 * Hook global do Reactor, por isso fica na autoconfiguração dos APPS (não no SDK, que é lib);
 * instalar de novo só substitui o mesmo consumidor (idempotente).
 */
public final class ReactorErroDescartado {

  private static final Logger LOG = LoggerFactory.getLogger(ReactorErroDescartado.class);

  private static final Consumer<Throwable> CONSUMIDOR = ReactorErroDescartado::tratar;

  private ReactorErroDescartado() {}

  public static void instalar() {
    Hooks.onErrorDropped(CONSUMIDOR);
  }

  static void tratar(Throwable erro) {
    if (porCancelamento(erro)) {
      LOG.debug("erro descartado após cancelamento: {}", erro.getClass().getSimpleName());
    } else {
      LOG.warn("erro descartado pelo Reactor: {}", erro.getClass().getSimpleName());
    }
  }

  private static boolean porCancelamento(Throwable erro) {
    for (Throwable t = erro; t != null; t = t.getCause() == t ? null : t.getCause()) {
      if (t instanceof CancellationException
          || (t.getMessage() != null && t.getMessage().contains("Request cancelled"))) {
        return true;
      }
    }
    return false;
  }
}
