package com.recorra.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Core da plataforma: domínio de recompra, agnóstico de ERP. */
@SpringBootApplication
public class CoreApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(CoreApiApplication.class, args);
  }
}
