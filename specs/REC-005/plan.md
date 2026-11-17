# REC-005 · Plano

1. Catálogo/convenções: `slf4j-api`, `logback-classic` (BOM); `logback-classic` como `testRuntimeOnly` no `recorra.java-conventions` (débito do REC-004); apps dependem de `:common`.
2. `common`: `Traceparent`, `TraceContext`/`MdcEscopo`, `ContextoDeTrabalho`.
3. `common`: `MascaradorDePii` + `MascaramentoJsonCustomizer` + `observabilidade-common.yml` (logstash JSON, access log do Tomcat off).
4. `common`: `TraceparentFilter`, `RequestLogFilter`, `RejeicaoDeNegocioException`, `ProblemaGlobalHandler`, `TraceparentRestClientInterceptor`, autoconfiguração.
5. `driver-sdk`: `TracingCanonicalPublisher` e `TraceparentExchangeFilter` (ponto de extensão do REC-004).
6. Testes: unitários do `common`, integração com app de teste + `CapturaDeLogJson` (testFixtures), testes de appender em core e driver, `TracingSdkTest`.
7. `./gradlew spotlessApply build` verde; cobertura do `common` >= 80%.
