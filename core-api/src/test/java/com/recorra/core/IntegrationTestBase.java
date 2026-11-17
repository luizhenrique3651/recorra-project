package com.recorra.core;

import java.util.Map;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base dos testes de integração do core (REC-003): app no perfil {@code test} contra um Postgres 16
 * real (Testcontainers 2.x). Um único container por JVM de teste, leve (alpine, 512 MB, tmpfs, sem
 * fsync) e reutilizável entre execuções locais (veja {@code tasks.test} do módulo). Os testes HTTP
 * usam {@code RestTestClient} (ADR-0002); nunca {@code TestRestTemplate}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  private static final long LIMITE_DE_MEMORIA = 512L * 1024 * 1024; // Docker local de 4 GB

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      // Manter em sincronia com a imagem do docker-compose.yml (postgres:16-alpine).
      new PostgreSQLContainer("postgres:16-alpine")
          .withReuse(true)
          .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
          .withCommand("postgres", "-c", "fsync=off", "-c", "shared_buffers=64MB")
          .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMemory(LIMITE_DE_MEMORIA));

  static {
    POSTGRES.start();
  }
}
