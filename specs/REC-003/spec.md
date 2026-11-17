# REC-003 · PostgreSQL + Flyway + Testcontainers + métricas + CI por path

Card: `tecDoc/roadmap-desenvolvimento-sdd.md` §REC-003 · EP-01 · 5 SP · branch `chore/REC-003-persistencia-ci`. ADRs: 0001 (item 3: validação no CI), 0002 (`RestTestClient`).

## Contratos

Sem endpoint de negócio. O que muda de visível: `GET /actuator/prometheus` (core e driver-bling) e o comportamento dos perfis.

| Item | Decisão |
|---|---|
| Perfis do `core-api` | `application.yml` (base) + `-dev`, `-test`, `-prod`; `spring.profiles.default=dev`. `ddl-auto=validate` em todos. |
| Credenciais | Nunca no YAML. `dev` lê `DB_USER`/`DB_PASSWORD` (e `DB_URL`, default `localhost:5432/recorra`); `prod` lê as três sem default; `test` usa o Testcontainers (`@ServiceConnection`). `docker-compose.yml` lê o mesmo `.env` (`.env.example` versionado, `.env` ignorado) e se recusa a subir sem eles. |
| Postgres local | `docker-compose.yml`: `postgres:16-alpine`, só em `127.0.0.1:5432`, limite de 512 MB, healthcheck. Sem Grafana/Prometheus (NÃO FAZER). |
| Flyway | `core-api/src/main/resources/db/migration/V0001__baseline.sql` (`CREATE EXTENSION pgcrypto`). Convenção das tabelas futuras (PK `uuid default gen_random_uuid()`, `criado_em`/`atualizado_em default now()`) documentada no próprio arquivo. `out-of-order=false` explícito, `validate-on-migrate=true`. |
| Versão de migration | `V<sprint 2 dígitos><seq 2 dígitos>__descricao_minuscula.sql`, por app, pela ordem de execução. Checagem em `common` (`testFixtures`): `ConvencaoDeMigrations.violacoes(dir)` reprova nome fora do padrão e versão duplicada; o `core-api` a executa sobre as próprias migrations (`MigrationsConvencaoTest`). Cada app com migrations (ex.: `driver-bling` no futuro) acrescenta o mesmo teste de uma linha. |
| Métricas | `micrometer-registry-prometheus` na convenção dos apps; `management.endpoints.web.exposure.include=health,info,prometheus`. |
| Springdoc em prod (débito REC-001 a) | `application-prod.yml` de core e driver: `springdoc.api-docs.enabled=false` e `springdoc.swagger-ui.enabled=false`. |
| Mockito como `-javaagent` (débito REC-001 b) | `recorra.java-conventions`: configuração `mockitoAgent` (mockito-core, sem transitivas, versão do BOM) passada ao `tasks.test` por `CommandLineArgumentProvider`; `-Xshare:off` nos testes (o agente no bootclasspath faz o CDS avisar "Sharing is only supported…"). Some o aviso de agente dinâmico. |
| Testcontainers 2.x | Artefatos novos do 2.x (`testcontainers-postgresql`, `testcontainers-junit-jupiter`), versão do BOM do Boot (2.0.5). Base `IntegrationTestBase`: **um** container por JVM de teste, `postgres:16-alpine`, 512 MB, `fsync=off`, `shared_buffers=64MB`, dados em tmpfs, `withReuse(true)`. O `tasks.test` do core liga `TESTCONTAINERS_REUSE_ENABLE=true` quando não há `CI` no ambiente (sem mexer em `~/.testcontainers.properties`): o container sobrevive entre execuções locais; no CI não há reuso. Se alterar uma migration já aplicada localmente (ex.: editar a `V0001`), o container reutilizado guarda o schema antigo e o Flyway falha no checksum: remova-o com o comando abaixo. Para liberar memória: `docker rm -f $(docker ps -aq --filter label=org.testcontainers=true --filter ancestor=postgres:16-alpine)`. |
| `RestTestClient` | Smoke tests do core e do driver migrados (`@AutoConfigureRestTestClient`, módulo `spring-boot-resttestclient` na convenção dos apps). Sem `TestRestTemplate`, sem `HttpClient`. |
| Validação de schemas/fixtures | `contracts` (teste): `ValidadorDeContrato` com `networknt/json-schema-validator` 3.0.9 (Jackson 3, versão no catálogo). (1) cada `*.schema.json` contra o meta-schema draft-07; (2) `src/test/resources/fixtures/<caminho-do-schema>/valida-*.json` devem passar e `invalida-*.json` devem falhar (formatos validados, `$ref` resolvido do disco via o `$id`). O CI roda exatamente `ContratoValidacaoTest`. |
| Gate do JaCoCo × código gerado (pendência REC-002) | Os `.java` gerados entram como **input rastreado** (`fileTree`, `PathSensitivity.RELATIVE`) de `jacocoTestReport` e `jacocoTestCoverageVerification`: mudar o schema invalida o relatório/gate. Nada é varrido na configuração. |

## CI (GitHub Actions) — `.github/workflows/ci.yml`

Um workflow, cinco jobs (`escopos`, `todos`, `core-api`, `driver-bling` e o final `ci-ok`). `escopos` calcula o que rodar com `scripts/ci/escopos.sh` (a lógica de path fica num script, não em YAML, justamente para ser testável aqui):

| Alterou | Roda |
|---|---|
| `contracts/**`, `build-logic/**`, `common/**`, `gradle/**`, raiz do build, `scripts/**`, `.github/**`, `messaging/**` e `drivers/driver-sdk/**` (nenhum job por módulo depende deles ainda; mover ao escopo do dependente quando passarem a depender), qualquer caminho desconhecido | job `todos`: validação de schemas/fixtures + `./gradlew build` (inclui o TestKit do `build-logic`) |
| `core-api/**` | só `core-api` (`:core-api:build`) |
| `drivers/driver-bling/**` | só `driver-bling` (`:drivers:driver-bling:build`) |
| só `docs/`, `specs/`, `tecDoc/`, `*.md` | nenhum job |

Em `push` para `develop`/`main` roda tudo (e o CI de push nunca é cancelado: `cancel-in-progress` só em PR). O diff usa `--no-renames`, para que mover um arquivo de `contracts/` liste o caminho antigo e dispare `todos`. Cache do Gradle por `gradle/actions/setup-gradle`; relatório JaCoCo sai como artefato (`jacoco-*`). Sem deploy.

Decisão: job condicional em vez de `on.paths` por workflow, porque job ignorado conta como sucesso nos *required checks* (um workflow filtrado por `paths` deixaria o check pendente para sempre num PR que não o dispara).

Custo do TestKit (pendência REC-002): o `build-logic:test` (~2 min) só roda no job `todos` (via `check` da raiz); os jobs por módulo (`:core-api:build`, `:drivers:driver-bling:build`) não o executam. Localmente `./gradlew build` continua executando tudo.

## Casos de teste (cenários do card)

| Cenário | Teste |
|---|---|
| Duas migrations com a mesma versão → falha | `ConvencaoDeMigrationsTest.versaoDuplicadaFalha` (+ nome fora do padrão, ignora não-`.sql`, diretório inexistente); `MigrationsConvencaoTest` (as reais passam) |
| Postgres de teste, app sobe em `test` → Flyway aplica a baseline | `PersistenciaIntegrationTest` (V0001 com `success`, nada pendente, `pgcrypto`, `gen_random_uuid`, `ddl-auto=validate`, `outOfOrder=false`) |
| PR só do driver-bling → só o job do driver | `scripts/ci/testar-escopos.sh` (12 casos) |
| Schema inválido / fixture fora do schema → falha | `ContratoValidacaoTest.schemaInvalidoFalha` e `.fixtureForaDoSchemaFalha` (provam que o validador falha); `.schemasDoContratoSaoValidos` e `.fixturesDoContratoConferemComOsSchemas` (o que o CI executa) |
| `/actuator/prometheus` expõe métricas | `MetricasEDocumentacaoTest`, `DriverBlingApplicationSmokeTest` |
| Springdoc ligado fora de prod e desligado em prod | `MetricasEDocumentacaoTest.springdocLigadoForaDeProd`; `ProdSemSpringdocTest` (core e driver) |
| Smoke tests em `RestTestClient` | `CoreApiApplicationSmokeTest`, `DriverBlingApplicationSmokeTest` |

## Como verificar

Localmente (JDK 21 do SDKMAN):

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/current PATH=$HOME/.sdkman/candidates/java/current/bin:$PATH
./gradlew spotlessApply build                      # tudo (precisa de Docker para o Testcontainers)
./gradlew :contracts:test --tests '*ContratoValidacaoTest'   # a validação que o CI roda
scripts/ci/testar-escopos.sh                       # filtros de path do CI
cp .env.example .env && docker compose up -d       # Postgres 16 em :5432 (edite a senha no .env)
set -a; source .env; set +a; ./gradlew :core-api:bootRun   # perfil dev; curl localhost:8080/actuator/prometheus
```

No GitHub (o workflow não foi executado aqui — não houve push):

1. Abra um PR de teste que altera só `drivers/driver-bling/**`: na aba *Actions*, `escopos` roda e só `driver-bling` executa (`todos` e `core-api` aparecem *skipped*).
2. PR que toca `contracts/src/main/schemas/**`: roda `todos`. Corrompa um schema (ex.: `"type": "nao-existe"`) ou uma fixture e confirme que o passo "Validar schemas…" falha.
3. PR só de `docs/`: nenhum job de build (só `escopos`).
4. Confirme que o artefato `jacoco-*` aparece no resumo da execução.

## Fora de escopo (NÃO FAZER do card)

`ddl-auto=update/create`; deploy contínuo (S9); segredo no YAML; Grafana/Prometheus no compose; `TestRestTemplate`; logging/traceId/PII (REC-005); ArchUnit (REC-004).

## Pendências / observações

- **Configuration cache não habilitado.** Testei: o único problema é o `generateJsonSchema2Pojo` do plugin `org.jsonschema2pojo` 1.3.3 ("cannot serialize Gradle script object references"), do plugin, fora do nosso código. A convenção do gate já não varre nada na configuração (input rastreado); basta o plugin corrigir para ligar `org.gradle.configuration-cache=true`.
- `/actuator/prometheus` está exposto sem autenticação e na mesma porta do app. Em prod isso exige restrição de rede/porta de management ou segurança: tratar no REC-074/segurança antes do deploy (S9).
- Actions fixadas por tag major (`@v4`); fixar por SHA é endurecimento para o REC de deploy.
- Required check no GitHub: marcar **só o `ci-ok`** (job final, `if: always()`). Job skipped conta como sucesso: sem o `ci-ok`, uma falha do `escopos` deixaria todos os builds skipped e o PR verde. Regra em `scripts/ci/resultado-final.sh`, provada em `testar-escopos.sh` (escopos failure/skipped/cancelled e build failure/cancelled → falha; builds skipped só com escopos success → ok). Ação manual do usuário.
- Container Postgres reutilizado fica de pé (≤512 MB) entre execuções locais; remover com o comando acima se a memória do Docker apertar.
- `driver-bling` ainda não tem banco/migrations (driver só lê do ERP); quando tiver, acrescentar o teste da convenção e a sua sequência `V0001`.
- O `git status` inicial desta árvore traz alterações que não são minhas: `AGENTS.md`, `tecDoc/*` e `docs/adr/0005-qodana-adiado-ate-servidor-docker.md` (provavelmente do maestro); não toquei nelas.

## Evidências (2026-11-10, JDK 21 do SDKMAN, Docker Desktop)

- `./gradlew spotlessApply build --rerun-tasks`: **BUILD SUCCESSFUL** (Spotless, SpotBugs, testes, JaCoCo, `verificarDadosDeCobertura`, `build-logic:check`).
- Testes: core-api 12, driver-bling 8, common 5, contracts 17, build-logic 5; 0 falhas.
- Sem aviso de agente dinâmico do Mockito nem de CDS nos logs de teste.
- `scripts/ci/testar-escopos.sh`: 12/12 casos ok.
- JaCoCo: core, driver e contracts não têm classe de produção fora de `*Application`/gerado, então o relatório não tem contador de linha (gate passa sem vacuidade pela regra "sem classes de produção"); a cobertura passa a valer quando surgir código de produção.
- O YAML do workflow foi só parseado (Ruby/YAML: jobs `escopos`, `todos`, `core-api`, `driver-bling`); a execução real depende de push.
