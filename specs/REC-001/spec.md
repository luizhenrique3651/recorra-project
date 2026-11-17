# REC-001 · Bootstrap do monorepo Gradle multi-módulo

Card: `tecDoc/roadmap-desenvolvimento-sdd.md` §REC-001 · Épico EP-01 · 5 SP · Sprint 0.

## Contratos
Nenhum endpoint de negócio, evento ou schema. Único endpoint exposto: `GET /actuator/health` em `core-api` (:8080) e `driver-bling` (:8081).

## Modelo tocado
Nenhum (sem domínio, sem banco).

## Estrutura (estado entregue)
`settings.gradle.kts` com **6 módulos**: `contracts`, `common`, `core-api`, `drivers:driver-sdk`, `drivers:driver-bling`, `messaging:wa-cloud`.
- Apps Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, actuator, springdoc 3.x): `core-api`, `driver-bling`.
- Libs `java-library`: demais módulos.
- Convenções em `build-logic/` (Java 21, Spotless, SpotBugs, JaCoCo ≥80% no `check`); versões em `gradle/libs.versions.toml`.
- Virtual threads ligadas.
- `messaging:wa-terceiro` removido (ADR-0003, item 3).

## Critérios de aceite
1. `./gradlew build` verde (Spotless, SpotBugs, testes, JaCoCo).
2. `./gradlew :core-api:bootRun` sobe só o core; `GET /actuator/health` = `UP`.
3. Idem `:drivers:driver-bling:bootRun` na 8081.
4. README com "como rodar".
5. `settings.gradle.kts` com 6 módulos; `wa-terceiro` ausente em settings, diretório e README.

## Casos de teste
- Smoke `@SpringBootTest` em `core-api` e `driver-bling` (contexto sobe; health UP).
- Build compila todos os módulos.

## Fora de escopo (NÃO FAZER)
Domínio/endpoints de negócio; acoplamento entre módulos; logging/Postgres/`RestTestClient` (REC-003); logging/PII (REC-005); bump para Boot 4.2.
