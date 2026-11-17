# REC-004 · Plano

1. Catálogo: Resilience4j 2.4.0 (core + reactor), `spring-webflux`, ArchUnit 1.5.1, WireMock 3.13.2.
2. `driver-sdk`: `IdempotencyKey`, `CanonicalPublisher` (+ `PublishResult`, `CanonicalMessage`, `DelegatingCanonicalPublisher`), `ErpClientConfig`, `ResilientErpClient`, `IncrementalPoller` + `CursorStore`, `WebhookReceiver` + `WebhookOutcome`.
3. Testes do SDK (WireMock para 429/5xx/I-O/circuito) até >= 80 %.
4. Módulo `architecture-tests` + regras + fixtures violadoras; mutação manual no core.
5. `driver-bling` consome o SDK (`DriverSdkConsumivelTest`).
6. CI: jobs por módulo rodam também `:architecture-tests:build`; caso novo em `testar-escopos.sh`.
7. `./gradlew spotlessApply build` verde.
