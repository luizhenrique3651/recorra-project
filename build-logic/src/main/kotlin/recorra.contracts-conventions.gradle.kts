// Módulo contracts (REC-002, ADR-0001): Java GERADO a partir de JSON Schema, único gerador:
// jsonschema2pojo. A saída vai para build/generated e nunca é versionada.

plugins {
  id("recorra.library-conventions")
  id("org.jsonschema2pojo")
}

jsonSchema2Pojo {
  // Ordem explícita: modelo antes de eventos/comandos. Assim os tipos de modelo (referenciados por
  // $ref) são gerados uma vez, no pacote base, e não no pacote de quem os referenciou primeiro.
  // Ao criar src/main/schemas/comandos (REC-010), acrescente-o depois de eventos.
  setSource(files("src/main/schemas/modelo", "src/main/schemas/eventos"))
  targetDirectory = layout.buildDirectory.dir("generated/sources/jsonschema2pojo").get().asFile
  targetPackage = "com.recorra.contracts"
  setAnnotationStyle("jackson2")
  setFileExtensions(".schema.json")
  setUseBigDecimals(true)
  setDateTimeType("java.time.OffsetDateTime")
  setIncludeAdditionalProperties(false)
  setIncludeHashcodeAndEquals(true)
  // toString gerado imprimiria todos os campos (PII em log): desligado de propósito (REC-005).
  setIncludeToString(false)
  setIncludeGeneratedAnnotation(false)
  setIncludeJsr305Annotations(false)
}

// Sem sobras: o diretório gerado é apagado antes de cada geração (nada de classe órfã de schema
// removido).
tasks.named("generateJsonSchema2Pojo") {
  doFirst { delete(layout.buildDirectory.dir("generated/sources/jsonschema2pojo")) }
}
