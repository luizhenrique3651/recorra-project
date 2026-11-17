package com.recorra.arch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova que as regras QUEBRAM (AssertionError = teste vermelho = build vermelho) quando existe uma
 * violação. As violadoras são classes de fixture em {@code src/test/.../violacao}, que o teste de
 * produção nunca importa (ele lê só as classes de produção dos módulos).
 */
class RegrasQuebramOBuildTest {

  private static JavaClasses fixtures(String... classes) {
    return new ClassFileImporter().importClasses(carregar(classes));
  }

  private static Class<?>[] carregar(String... nomes) {
    Class<?>[] tipos = new Class<?>[nomes.length];
    for (int i = 0; i < nomes.length; i++) {
      try {
        tipos[i] = Class.forName(nomes[i]);
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
    }
    return tipos;
  }

  private static void quebra(ArchRule regra, String violadora, String alvo) {
    assertThatThrownBy(() -> regra.check(fixtures(violadora, alvo)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(violadora.substring(violadora.lastIndexOf('.') + 1));
  }

  @Test
  @DisplayName("Dado o core importando um driver / Quando as regras rodam / Então o build falha")
  void coreImportandoDriverQuebra() {
    quebra(
        RegrasDeArquitetura.CORE_NAO_IMPORTA_DRIVER,
        "com.recorra.core.violacao.CoreImportaDriver",
        "com.recorra.driver.violacao.DriverAlvo");
  }

  @Test
  @DisplayName("Dado um driver importando o core / Quando as regras rodam / Então o build falha")
  void driverImportandoCoreQuebra() {
    quebra(
        RegrasDeArquitetura.DRIVER_NAO_IMPORTA_CORE,
        "com.recorra.driver.violacao.DriverImportaCore",
        "com.recorra.core.violacao.CoreAlvo");
  }

  @Test
  @DisplayName("Dado o contrato importando o core / Quando as regras rodam / Então o build falha")
  void contratoImportandoCoreQuebra() {
    quebra(
        RegrasDeArquitetura.CONTRATO_E_NEUTRO,
        "com.recorra.contracts.violacao.ContratoImportaCore",
        "com.recorra.core.violacao.CoreAlvo");
  }

  @Test
  @DisplayName("Dado o SDK importando um ERP / Quando as regras rodam / Então o build falha")
  void sdkImportandoErpQuebra() {
    quebra(
        RegrasDeArquitetura.SDK_NAO_CONHECE_ERP,
        "com.recorra.driver.sdk.violacao.SdkImportaBling",
        "com.recorra.driver.bling.violacao.BlingAlvo");
  }

  @Test
  @DisplayName(
      "Dado core e driver falando só pelo contrato / Quando as regras rodam / Então passam")
  void apenasOContratoComoPonteNaoViola() {
    JavaClasses legitimas =
        fixtures(
            "com.recorra.core.violacao.CoreUsaContrato",
            "com.recorra.driver.violacao.DriverUsaContrato");

    for (ArchRule regra : RegrasDeArquitetura.TODAS) {
      assertThatCode(() -> regra.allowEmptyShould(true).check(legitimas))
          .doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("Dado o common importando o core / Quando as regras rodam / Então o build falha")
  void commonImportandoCoreQuebra() {
    quebra(
        RegrasDeArquitetura.COMMON_E_NEUTRO,
        "com.recorra.common.violacao.CommonImportaCore",
        "com.recorra.core.violacao.CoreAlvo");
  }
}
