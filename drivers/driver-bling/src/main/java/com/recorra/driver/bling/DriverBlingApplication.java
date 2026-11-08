package com.recorra.driver.bling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Driver do Bling: traduz o ERP para o contrato canônico. Somente leitura no ERP. */
@SpringBootApplication
public class DriverBlingApplication {

  public static void main(String[] args) {
    SpringApplication.run(DriverBlingApplication.class, args);
  }
}
