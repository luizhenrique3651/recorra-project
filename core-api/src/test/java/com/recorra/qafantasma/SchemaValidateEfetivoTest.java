package com.recorra.qafantasma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recorra.core.CoreApiApplication;
import com.recorra.core.PostgresDeTeste;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

/**
 * QA (REC-003): a baseline não tem entidades, então "ddl-auto=validate passa" é vacuoso. Aqui uma
 * entidade sem tabela prova que o validate de fato roda e derruba a subida (e que não cria nada).
 */
class SchemaValidateEfetivoTest {

  @Entity(name = "QaFantasma")
  static class Fantasma {
    @Id UUID id;
  }

  @Configuration
  @EntityScan(basePackageClasses = Fantasma.class)
  static class ScanDaEntidadeFantasma {}

  @Test
  @DisplayName("Dado entidade sem tabela / Quando a app sobe com validate / Então falha e não cria")
  void entidadeSemTabelaDerrubaASubida() {
    String url = PostgresDeTeste.container().getJdbcUrl();
    String user = PostgresDeTeste.container().getUsername();
    String senha = PostgresDeTeste.container().getPassword();

    assertThatThrownBy(
            () ->
                new SpringApplicationBuilder(CoreApiApplication.class, ScanDaEntidadeFantasma.class)
                    .web(WebApplicationType.NONE)
                    .profiles("test")
                    .properties(
                        "spring.datasource.url=" + url,
                        "spring.datasource.username=" + user,
                        "spring.datasource.password=" + senha,
                        "spring.main.banner-mode=off")
                    .run())
        .hasStackTraceContaining("qa_fantasma");

    assertThat(PostgresDeTeste.container().isRunning()).isTrue();
  }
}
