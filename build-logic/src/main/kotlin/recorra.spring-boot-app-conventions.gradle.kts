// Serviços implantáveis (core-api, driver-bling): Spring Boot + web + actuator + springdoc.

plugins {
  id("recorra.java-conventions")
  id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = libs.findLibrary(alias).get()

dependencies {
  implementation(lib("spring-boot-starter-webmvc"))
  implementation(lib("spring-boot-starter-actuator"))
  implementation(lib("springdoc-webmvc-ui"))

  testImplementation(lib("spring-boot-starter-test"))
  testImplementation(lib("spring-boot-starter-webmvc-test"))
}

// Só o jar executável; o "-plain" não é usado por ninguém.
tasks.named<Jar>("jar") { enabled = false }
