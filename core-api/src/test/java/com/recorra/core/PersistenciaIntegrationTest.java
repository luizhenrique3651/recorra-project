package com.recorra.core;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

class PersistenciaIntegrationTest extends IntegrationTestBase {

  @Autowired Flyway flyway;
  @Autowired DataSource dataSource;

  @Value("${spring.jpa.hibernate.ddl-auto}")
  String ddlAuto;

  @Test
  @DisplayName(
      "Dado o Postgres de teste / Quando a app sobe em test / Então Flyway aplica a baseline")
  void flywayAplicaABaseline() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    Integer baselines =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '0001' and success",
            Integer.class);

    assertThat(baselines).isEqualTo(1);
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  @DisplayName("Dado a baseline / Quando consulto extensões / Então pgcrypto está instalada")
  void pgcryptoInstalada() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    Integer n =
        jdbc.queryForObject(
            "select count(*) from pg_extension where extname = 'pgcrypto'", Integer.class);
    String uuid = jdbc.queryForObject("select gen_random_uuid()::text", String.class);

    assertThat(n).isEqualTo(1);
    assertThat(uuid).hasSize(36);
  }

  @Test
  @DisplayName("Dado a configuração / Quando leio / Então ddl-auto=validate e outOfOrder=false")
  void politicasDeSchema() {
    assertThat(ddlAuto).isEqualTo("validate");
    assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
  }
}
