# REC-002 · Plano

1. `libs.versions.toml`: `jsonschema2pojo` (plugin 1.3.3) e `jackson-annotations`; plugin aplicado em `contracts` (provar Gradle 9; se falhar, task no `build-logic`).
2. Schemas (`modelo/`, `eventos/`), AsyncAPI e OpenAPI mínimos em `contracts/src/main`.
3. `contracts/build.gradle.kts`: configurar o gerador, `api(jackson-annotations)`, testes com Jackson 3.
4. `./gradlew :contracts:build`: confirmar geração e compilação.
5. Testes do `contracts` (round-trip, `$ref`, regras do schema).
6. `core-api` e `driver-bling`: `implementation(project(":contracts"))` + teste de round-trip com o `ObjectMapper` do Boot.
7. Gate do JaCoCo no `recorra.java-conventions`: exclusão de `build/generated/**` e task `verificarDadosDeCobertura`.
8. Teste TestKit no `build-logic` provando (a) e (b); build raiz passa a executá-lo.
9. `./gradlew spotlessApply build` (JDK 21 do SDKMAN) verde; registrar evidências na spec.
