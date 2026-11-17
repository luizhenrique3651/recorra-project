package com.recorra.core;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** Ponte pública para testes de outros pacotes reutilizarem o container do IntegrationTestBase. */
public final class PostgresDeTeste {

  private PostgresDeTeste() {}

  public static PostgreSQLContainer container() {
    return IntegrationTestBase.POSTGRES;
  }
}
