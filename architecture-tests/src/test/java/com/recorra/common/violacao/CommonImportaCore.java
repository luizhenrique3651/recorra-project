package com.recorra.common.violacao;

import com.recorra.core.violacao.CoreAlvo;

/** Fixture VIOLADORA: o common (base técnica) depende do core. */
public class CommonImportaCore {
  CoreAlvo core = new CoreAlvo();
}
