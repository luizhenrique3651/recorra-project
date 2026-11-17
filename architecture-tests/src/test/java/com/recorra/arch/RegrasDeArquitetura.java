package com.recorra.arch;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * Regras de arquitetura do Recorra (REC-004). A única ponte entre core e drivers é o pacote {@code
 * com.recorra.contracts} (ADR-0001). As mesmas regras rodam sobre o código real e, nos testes de "a
 * regra quebra", sobre classes violadoras de fixture.
 */
final class RegrasDeArquitetura {

  static final String CORE = "com.recorra.core..";
  static final String DRIVERS = "com.recorra.driver..";
  static final String SDK = "com.recorra.driver.sdk..";
  static final String CONTRATO = "com.recorra.contracts..";
  static final String COMMON = "com.recorra.common..";

  /** O core nunca conhece um driver (nem ERP nenhum). */
  static final ArchRule CORE_NAO_IMPORTA_DRIVER =
      noClasses()
          .that()
          .resideInAPackage(CORE)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(DRIVERS)
          .because("core-api nunca conhece um ERP: a única ponte é com.recorra.contracts");

  /** Driver não importa o domínio do core. */
  static final ArchRule DRIVER_NAO_IMPORTA_CORE =
      noClasses()
          .that()
          .resideInAPackage(DRIVERS)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(CORE)
          .because("drivers falam com o core só pelo contrato canônico");

  /** O contrato é neutro: não depende de core nem de driver. */
  static final ArchRule CONTRATO_E_NEUTRO =
      noClasses()
          .that()
          .resideInAPackage(CONTRATO)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(CORE, DRIVERS)
          .because("o contrato é gerado do schema e não conhece ninguém");

  /** O common é base técnica: não conhece core, driver nem contrato. */
  static final ArchRule COMMON_E_NEUTRO =
      noClasses()
          .that()
          .resideInAPackage(COMMON)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(CORE, DRIVERS, CONTRATO)
          .because("common é infraestrutura reutilizável, sem domínio nem ERP");

  /** O SDK é genérico: não se acopla a nenhum driver específico de ERP. */
  static final ArchRule SDK_NAO_CONHECE_ERP =
      noClasses()
          .that()
          .resideInAPackage(SDK)
          .should()
          .dependOnClassesThat(resideInAPackage(DRIVERS).and(not(resideInAPackage(SDK))))
          .because("o driver-sdk não pode se acoplar a um ERP específico");

  static final List<ArchRule> TODAS =
      List.of(
          CORE_NAO_IMPORTA_DRIVER,
          DRIVER_NAO_IMPORTA_CORE,
          CONTRATO_E_NEUTRO,
          COMMON_E_NEUTRO,
          SDK_NAO_CONHECE_ERP);

  private RegrasDeArquitetura() {}
}
