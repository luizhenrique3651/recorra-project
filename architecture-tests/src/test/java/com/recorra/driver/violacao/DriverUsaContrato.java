package com.recorra.driver.violacao;

import com.recorra.contracts.VendaCanonica;

/** Fixture LEGÍTIMA: o driver fala com o core só pelo contrato. */
public class DriverUsaContrato {
  VendaCanonica venda = new VendaCanonica();
}
