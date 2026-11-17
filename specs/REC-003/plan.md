# REC-003 · Plano

1. Catálogo (`libs.versions.toml`): JPA/Flyway/Postgres, Prometheus, Testcontainers, `RestTestClient`, `json-schema-validator`, `mockito-core`.
2. `build-logic`: agente do Mockito no `tasks.test`; `.java` gerados como input rastreado do gate do JaCoCo; Prometheus + `RestTestClient` na convenção dos apps; `deleteRecursively` sem script na geração (tentativa de configuration cache).
3. `common`: `ConvencaoDeMigrations` (testFixtures) + teste.
4. `core-api`: dependências, perfis `dev/test/prod`, `V0001__baseline.sql`, `IntegrationTestBase` (Testcontainers), testes de persistência/métricas/springdoc/prod/convenção; `docker-compose.yml` + `.env.example`.
5. `driver-bling`: métricas, `application-prod.yml`, smoke em `RestTestClient`, teste de prod.
6. `contracts`: `ValidadorDeContrato` + fixtures + `ContratoValidacaoTest`.
7. CI: `scripts/ci/escopos.sh` + `testar-escopos.sh`; `.github/workflows/ci.yml`.
8. `./gradlew spotlessApply build` verde; evidências abaixo.
