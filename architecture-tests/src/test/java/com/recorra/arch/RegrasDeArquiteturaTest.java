package com.recorra.arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Roda as regras sobre o código REAL de produção dos módulos (as classes compiladas chegam pelas
 * propriedades {@code recorra.classes.*} que o Gradle injeta e rastreia como input).
 */
class RegrasDeArquiteturaTest {

  static JavaClasses producao;

  @BeforeAll
  static void importaOCodigoDeProducao() {
    List<Path> raizes =
        System.getProperties().stringPropertyNames().stream()
            .filter(k -> k.startsWith("recorra.classes."))
            .sorted()
            .map(k -> Path.of(System.getProperty(k)))
            .filter(Files::isDirectory) // módulo sem classe de produção (ex.: só package-info)
            .toList();
    // Sem módulos analisados as regras passariam no vazio: o build precisa falhar.
    assertThat(raizes).as("módulos injetados pelo Gradle (recorra.classes.*)").isNotEmpty();
    producao =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPaths(raizes);
  }

  @Test
  @DisplayName("Dado o core / Quando analiso os imports / Então não importa com.recorra.driver..")
  void coreNaoImportaDriver() {
    RegrasDeArquitetura.CORE_NAO_IMPORTA_DRIVER.check(producao);
  }

  @Test
  @DisplayName("Dado os drivers / Quando analiso os imports / Então não importam o domínio do core")
  void driverNaoImportaCore() {
    RegrasDeArquitetura.DRIVER_NAO_IMPORTA_CORE.check(producao);
  }

  @Test
  @DisplayName(
      "Dado o contrato / Quando analiso os imports / Então não depende de core nem de driver")
  void contratoENeutro() {
    RegrasDeArquitetura.CONTRATO_E_NEUTRO.check(producao);
  }

  @Test
  @DisplayName("Dado o driver-sdk / Quando analiso os imports / Então não conhece nenhum ERP")
  void sdkNaoConheceErp() {
    RegrasDeArquitetura.SDK_NAO_CONHECE_ERP.check(producao);
  }

  @Test
  @DisplayName("Dado o código real / Quando importo / Então core, driver, sdk e contrato estão lá")
  void importacaoNaoEhVaziaPorPacote() {
    for (String pacote :
        List.of(
            "com.recorra.core",
            "com.recorra.driver.sdk",
            "com.recorra.driver.bling",
            "com.recorra.contracts")) {
      assertThat(producao.stream().filter(c -> c.getPackageName().startsWith(pacote)))
          .as("classes de produção em " + pacote)
          .isNotEmpty();
    }
  }

  @Test
  @DisplayName("Dado um conjunto sem classes / Quando as regras rodam / Então falham (sem vácuo)")
  void regrasSobreConjuntoVazioFalham() {
    JavaClasses vazio = new ClassFileImporter().importClasses();
    for (var regra : RegrasDeArquitetura.TODAS) {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> regra.check(vazio))
          .isInstanceOf(AssertionError.class);
    }
  }

  @Test
  @DisplayName(
      "Dado o common / Quando analiso os imports / Então não depende de core, driver nem contrato")
  void commonENeutro() {
    RegrasDeArquitetura.COMMON_E_NEUTRO.check(producao);
  }
}
