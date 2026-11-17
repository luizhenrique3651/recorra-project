package com.recorra.contracts.violacao;

import com.recorra.core.violacao.CoreAlvo;

/** Fixture VIOLADORA: o contrato (neutro) depende do core. */
public class ContratoImportaCore {
  CoreAlvo core = new CoreAlvo();
}
