package com.recorra.driver.sdk;

import java.util.Optional;

/** Guarda o cursor (ponto de leitura) do {@link IncrementalPoller}; a persistência é do driver. */
public interface CursorStore {

  Optional<String> load();

  void save(String cursor);
}
