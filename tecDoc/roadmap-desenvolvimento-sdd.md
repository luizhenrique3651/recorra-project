# Recorra — Roadmap & Sprint Board (Spec-Driven Development)
### Plataforma de Inteligência de Recompra · Arquitetura driver-based · Monorepo

**Luiz Henrique Albuquerque Coelho** — Desenvolvedor Freelancer · Java / Spring Boot
*Novembro 2026 — Documento interno, uso exclusivo do desenvolvimento (humano + assistido por IA)*

> Codinome de trabalho: **Recorra** (provisório). Este documento é a fonte para o desenvolvimento assistido por IA (Gemini Pro / Claude Code) em regime **Spec-Driven Development**: cada card vira uma `spec.md`, depois `plan.md`, depois código. As seções **Instruções de Desenvolvimento**, **Cenários de Teste** e **NÃO FAZER** de cada card são vinculantes.

| EPIC | STORY | TASK | SPIKE | BUG |
|---|---|---|---|---|
| Funcionalidade estratégica de alto nível | Funcionalidade com valor ao usuário | Atividade técnica necessária | Investigação ou PoC técnica | Correção de defeito |

---

## 1. Core de Negócio

Esta seção dá ao desenvolvedor (humano ou IA) o **contexto de domínio** necessário para implementar qualquer card sem precisar inferir regras de negócio.

### 1.1 O que o produto faz

Recorra é uma **plataforma horizontal de inteligência de recompra e relacionamento proativo** para o varejo B2C. Ela lê as vendas que o comércio já registra no seu ERP/PDV, consolida o histórico por cliente, **descobre o padrão de recompra de cada cliente** e age **antes** do produto acabar — enviando a mensagem certa, no canal certo (WhatsApp), com a oferta certa.

O diferencial não é cadastro nem cashback (commodities). É a **recompra antecipada automática**: tirar a inteligência de vendas da cabeça do vendedor e transformá-la num ativo do comércio, disponível para todos os clientes, com visibilidade para o dono.

### 1.2 Público e verticais

Qualquer varejo B2C com **consumível de recompra previsível e cliente identificável**: pet shop, farmácia/suplementos, cosmético/beleza, tabacaria/vape, agro/veterinária, auto peças, ótica, café especial. O produto é **vertical-agnóstico**; particularidades de cada vertical entram como **configuração** (`RegraVertical`), nunca como código do núcleo.

### 1.3 Conceitos de domínio (ubiquitous language)

| Conceito | Definição |
|---|---|
| **Conta** | Entidade contratante (um negócio ou uma rede). Agrupa comércios. |
| **Comércio** | Unidade/loja. É o **tenant**: todo dado é isolado por `comercio_id`. Tem um `erp` e um `vertical`. |
| **ERP/PDV** | Sistema que o comércio já usa (ex.: Bling). Fonte da verdade transacional. A plataforma é **read-only** nele. |
| **Driver** | Adaptador (anti-corruption layer) de um ERP. Traduz o ERP para o **modelo canônico** e empurra eventos ao core. |
| **Modelo canônico** | Representação neutra de Cliente/Produto/Venda/Estoque, independente de qualquer ERP. O core só conhece isto. |
| **Cliente** | Comprador identificável (nome + WhatsApp no mínimo; CPF/e-mail opcionais). |
| **Venda / ItemVenda** | Transação consolidada e seus itens, vinculada a um Cliente. |
| **CicloRecompra** | Por cliente+produto: intervalo médio entre compras e a próxima compra prevista. |
| **PerfilConsumoCliente** | Agregado por cliente: ticket médio, frequência, segmento, última compra. |
| **AçãoRecomendada** | "Próxima ação" gerada pela inteligência (reposição, cross-sell, reativação). Tem ciclo de vida. |
| **Mensagem** | Comunicação enviada (via WhatsApp) referente a uma ação. |
| **RegraVertical** | Configuração por comércio/vertical (janela de reposição, regras de recomendação). |
| **MessagingPort** | Porta de saída para mensagens; adapters: WhatsApp oficial (Meta) ou terceiro (número dedicado). |

### 1.4 A lógica de inteligência (o coração do negócio)

**Recorrência (por cliente × produto).** A partir do histórico consolidado, ordenado por data, calcula-se o intervalo entre compras consecutivas do mesmo produto. `intervalo_medio_dias` = média (ou mediana, mais robusta a outliers) dos gaps. `proxima_prevista = ultima_compra + intervalo_medio_dias`. A `confianca` cresce com o número de ocorrências (ex.: 1 compra = sem ciclo; 2 = baixa; ≥3 = utilizável).

**Ação de reposição.** Para cada ciclo com confiança suficiente, quando `hoje ≥ proxima_prevista − N` (N = janela configurável por comércio, ex.: 5 dias num ciclo de 40), gera-se uma `AçãoRecomendada(REPOSICAO)` — **se** o cliente tiver opt-in e não existir ação ativa idêntica.

**Segmentação (Fase 2).** Classifica o cliente por frequência/volume/mix: profissional (alta recorrência, LTV curto, sensível a preço — reter) vs. consumidor final/hobbysta (baixa recorrência, LTV longo — incentivar retorno) vs. novo/inativo.

**Cross-sell por margem (Fase 2).** Dado o que o cliente compra, sugerir produto equivalente de maior margem, ou complemento natural (quem compra X costuma precisar de Y).

**Recomendação por vertical (Fase 2/3).** Regra configurável por atributo do produto/cliente (ex.: produto adequado a um atributo do cliente). Entra por `RegraVertical`, nunca hardcoded.

### 1.5 Fluxo ponta a ponta

```
Venda no ERP → driver (captura + normaliza) → evento canônico → core (consolida)
→ motor de inteligência (recorrência/perfil) → geração de ação → MessagingPort → WhatsApp
→ status de entrega → feedback do dono (convertida/descartada) → aprendizado
```

### 1.6 Regras de negócio transversais (invariantes)

1. **Read-only no ERP.** A plataforma nunca escreve venda/nota no ERP.
2. **Agnóstico de ERP.** O core nunca conhece um ERP concreto. Toda especificidade mora num driver. Proibido `if (erp == "bling")` no core.
3. **Idempotência.** Reprocessar um evento de venda não duplica nem corrompe estado (chave: `erp_venda_id` por comércio).
4. **Multi-tenant.** Todo dado é isolado por `comercio_id` (RLS). Nenhuma consulta cruza comércios.
5. **Consentimento (LGPD).** Nenhuma mensagem de marketing sem opt-in. Opt-out é imediato e definitivo até novo opt-in.
6. **WhatsApp com regras da Meta.** Fora da janela de 24h, só template aprovado.
7. **Minimização.** MVP coleta só nome + WhatsApp (+CPF/e-mail quando houver).

### 1.7 Glossário técnico rápido

ACL = Anti-Corruption Layer · Canônico = modelo neutro de domínio · Outbox = padrão para publicar evento junto à transação sem dual-write · RLS = Row-Level Security · DLQ = Dead-Letter Queue · Janela N = antecedência em dias do gatilho de reposição.

---

## 2. Roadmap — Visão Geral

8 sprints de 2 semanas no MVP (+ backlog de Fase 2). Regime parcial: ~14–18h/semana. Velocidade conservadora: **~20–24 SP/sprint**. O prazo total do MVP é **3 a 5 meses** — a faixa superior cobre o risco da integração com o ERP (descoberta do Bling) e a curva da camada de abstração (drivers).

| Spr. | Nome | Fase | SP | Foco principal |
|---|---|---|---|---|
| **0** | Fundação do Monorepo | Infra | ~20 | Monorepo Gradle · contracts (geração) · driver-sdk · Postgres/Flyway · CI por path · observabilidade · ArchUnit |
| **1** | Contrato Canônico, Tenant & IAM | Core | ~24 | Contrato v0 · multi-tenant+RLS · IAM/RBAC · onboarding de comércio · spike Bling |
| **2** | Ingestão Canônica & driver-bling | Core+Driver | ~24 | Consumo canônico idempotente · OAuth Bling · captura (webhook/backfill) → canônico |
| **3** | Motor de Recorrência | Core | ~20 | Cálculo de ciclo/perfil · consolidação diária · recomputo reativo · consultas |
| **4** | Ações, Outbox & Broker | Core | ~22 | AçãoRecomendada · gerador de reposição · outbox · RabbitMQ/DLQ · fila+feedback |
| **5** | Mensageria & WhatsApp | Core+Adapters | ~23 | MessagingPort · adapter oficial · adapter terceiro · opt-in · envio · status |
| **6** | Dashboard, Higienização & Config | Core | ~21 | Dashboards · higienização de base · regras por vertical |
| **7** | Hardening, LGPD, Piloto & 2º Driver | Qualidade | ~22 | Testes e2e · resiliência · LGPD · deploy · **prova do 2º driver** |
| | **TOTAL MVP** | | **~176 SP** | |

### 2.1 Fases e Marcos

| Fase | Sprints | Marco de saída | Risco |
|---|---|---|---|
| Fundação | 0 | Monorepo buildando; contrato gerando código; CI por path | Baixo |
| Contrato & Base | 1 | Contrato v0 fechado; isolamento por comércio (RLS); IAM | Médio |
| Ingestão | 2 | Venda no Bling consolidada no core **sem o core conhecer Bling** | Alto (dep. Bling) |
| Inteligência | 3–4 | Recorrência calculada; ação de reposição gerada | Médio |
| Relacionamento | 5–6 | Mensagem no WhatsApp a partir da ação; dashboard | Médio |
| Prontidão | 7 | Piloto em produção; **2º driver sem tocar no core** | Alto |

---

## 3. Convenções do Board

### 3.1 Escala de Story Points

| SP | Esforço | Complexidade | Exemplo |
|---|---|---|---|
| **1** | ~2h | Trivial | Campo novo num DTO; ajuste de validação |
| **2** | ~4h | Simples | Endpoint GET sem regra; config de env |
| **3** | ~6h | Moderado | Endpoint com regra simples; migration de uma entidade |
| **5** | ~10h | Complexo | Módulo com CRUD + validações; consumer idempotente |
| **8** | ~16h | Muito complexo | Integração externa (OAuth ERP, WhatsApp); captura webhook+backfill |
| **13** | ~26h+ | Épico — dividir | Se chegou aqui, quebre em cards menores |

### 3.2 Workflow de um card

`BACKLOG → TODO → IN PROGRESS → IN REVIEW → TESTING → DONE`

No modo assistido por IA: o agente gera `spec.md` (contratos, modelo tocado, casos de teste), depois `plan.md` (passos), implementa, abre PR pequeno. **Um card = um PR verde.** Não iniciar o próximo card com o anterior incompleto.

### 3.3 Definition of Done (DoD) — global

- Código na branch correta (`feature/REC-XXX-descricao` → `develop`).
- Critérios de aceite verificados.
- **Cenários de teste do card implementados** (unit + integração) e verdes.
- Cobertura ≥ 80% de linha no módulo tocado (JaCoCo).
- Sem violação de ArchUnit (fronteiras core↔driver↔contrato).
- Sem violação de lint/format (Spotless) nem SpotBugs crítico.
- Endpoints documentados (springdoc/OpenAPI); eventos refletidos no `contracts`.
- Sem segredo hardcoded; PII não logada.
- PR mergeado em `develop` sem conflito; migration aplicada em dev.

### 3.4 Branches e commits

| Tipo | Branch | Commit (exemplo) |
|---|---|---|
| Feature | `feature/REC-XXX-descricao` | `feat(REC-021): bling oauth connect/callback/refresh` |
| Fix | `fix/REC-XXX-descricao` | `fix(REC-023): dedupe sale by erp_venda_id` |
| Chore | `chore/REC-XXX-descricao` | `chore(REC-003): ci path-based triggers` |
| Test | `test/REC-XXX-descricao` | `test(REC-031): recurrence calculator edge cases` |
| Refactor | `refactor/REC-XXX-descricao` | `refactor(REC-020): extract canonical mapper` |

### 3.5 Prioridades

🔴 CRITICAL (bloqueia o sprint) · 🟠 HIGH · 🔵 MEDIUM · ⚪ LOW.

---

## 4. Epics

| ID | Nome | Escopo | Sprints |
|---|---|---|---|
| **EP-01** | Fundação & DevOps | Monorepo, contracts/geração, driver-sdk, Postgres/Flyway, CI/CD por path, observabilidade, ArchUnit, deploy | 0, 7 |
| **EP-02** | Tenancy & IAM | Conta/Comércio, RLS, OIDC/JWT, RBAC, onboarding | 1 |
| **EP-03** | Contrato Canônico | Modelo + eventos canônicos, versionamento, geração de código | 0, 1 |
| **EP-04** | Drivers (ACL de ERP) | driver-bling (auth + captura + normalização), driver-sdk, 2º driver | 2, 7 |
| **EP-05** | Ingestão & Catálogo | Consumo canônico, consolidação idempotente, espelho de catálogo/estoque | 2 |
| **EP-06** | Inteligência | Recorrência, perfil, segmentação, geração de ações | 3, 4 |
| **EP-07** | Engajamento | MessagingPort, adapters WhatsApp, opt-in, envio, status | 5 |
| **EP-08** | Dashboard & Config | Agregações, higienização, regras por vertical | 6 |
| **EP-09** | Qualidade & LGPD | Testes e2e, resiliência, LGPD, piloto | 7 |

---
---

## Sprint 0 · Fundação do Monorepo
*Fase: Infraestrutura · Semanas 1–2 · ~20 SP*

🎯 **Meta:** monorepo multi-módulo buildando; `contracts` gerando classes a partir do schema; `driver-sdk` base; Postgres/Flyway; CI por path; observabilidade; ArchUnit impondo fronteiras. Sem regra de negócio.

### REC-001 · [TASK] · 🔴 CRITICAL · 5 SP · EP-01
**Bootstrap do monorepo Gradle multi-módulo**

**Descrição.** Criar o repositório único com os módulos definitivos e cada serviço Spring Boot subindo com health check. É a fundação sobre a qual todo o resto é construído.

**Instruções de desenvolvimento.**
- Gradle (Kotlin DSL). `settings.gradle.kts` incluindo: `contracts`, `common`, `core-api`, `drivers:driver-sdk`, `drivers:driver-bling`, `messaging:wa-cloud`, `messaging:wa-terceiro`.
- `build-logic/` com convention plugins: Java 21 toolchain, Spotless, JaCoCo, SpotBugs, versões centralizadas (version catalog `libs.versions.toml`).
- `core-api` e `driver-bling` são apps Spring Boot 3.3 com `spring-boot-starter-web`, `-actuator`, `springdoc`. Demais módulos são libs (`java-library`).
- Pacotes: `com.recorra.core.*`, `com.recorra.driver.bling`, `com.recorra.contracts`, `com.recorra.common`.
- Ligar virtual threads (`spring.threads.virtual.enabled=true`).

**Critérios de aceite.**
- `./gradlew build` compila todos os módulos sem erro.
- `core-api` e `driver-bling` sobem e respondem `GET /actuator/health` = `{"status":"UP"}`.
- `./gradlew :core-api:bootRun` sobe só o core.
- README com "como rodar".

**Cenários de teste.**
- `Dado o monorepo / Quando rodo o build / Então todos os módulos compilam`.
- `Dado core-api no ar / Quando GET /actuator/health / Então 200 UP`.
- Smoke test de contexto Spring (`@SpringBootTest` mínimo) em core-api e driver-bling.

**NÃO FAZER.** Não criar domínio/endpoints de negócio; não acoplar módulos entre si; não colocar tudo num módulo só.

**Labels:** infra, gradle, monorepo, setup

---

### REC-002 · [TASK] · 🔴 CRITICAL · 5 SP · EP-03
**Esqueleto do `contracts` com geração de código a partir de schema**

**Descrição.** O contrato canônico é a fonte da verdade sobre o fio entre core e drivers. Deve ser definido como **schema neutro** e as classes Java **geradas** no build — nunca escritas à mão.

**Instruções de desenvolvimento.**
- Estrutura: `contracts/src/main/asyncapi/` (eventos, JSON Schema) e `contracts/src/main/openapi/` (ingestão síncrona).
- Geração: `openapi-generator` (modelos) e/ou `jsonschema2pojo` no build do módulo `contracts`; saída em `build/generated` adicionada ao sourceSet.
- Definir **um** evento de exemplo end-to-end: `venda.canonica.registrada` com um payload mínimo, só para validar o pipeline de geração.
- Publicar `contracts` como dependência interna consumível por `core-api` e `drivers:*`.

**Critérios de aceite.**
- `./gradlew :contracts:build` gera as classes Java a partir do schema.
- `core-api` e `driver-bling` importam e compilam contra os tipos gerados.
- Nenhum DTO canônico escrito à mão no `contracts`.

**Cenários de teste.**
- `Dado o schema de venda.canonica.registrada / Quando buildo contracts / Então a classe gerada existe e é serializável (round-trip JSON)`.
- Teste de serialização/desserialização Jackson do evento gerado.

**NÃO FAZER.** Não escrever POJOs canônicos manualmente; não colocar lógica no módulo contracts; não incluir campos específicos de Bling.

**Labels:** contracts, codegen, asyncapi, openapi

---

### REC-003 · [TASK] · 🔴 CRITICAL · 5 SP · EP-01
**PostgreSQL + Flyway + Testcontainers + observabilidade + CI por path**

**Descrição.** Infra de persistência, baseline de schema, base de testes de integração e pipeline que só rebuilda o que mudou.

**Instruções de desenvolvimento.**
- `docker-compose.yml` com Postgres 16. Datasource por perfil (`dev`/`test`/`prod`), `ddl-auto=validate`.
- Flyway `V0001__baseline.sql` (extensão `pgcrypto` p/ `gen_random_uuid()`); convenção: PK uuid, `criado_em`/`atualizado_em` com default `now()`.
- Classe base de teste com Testcontainers (Postgres reutilizável).
- Observabilidade: Micrometer + `/actuator/prometheus`; Logback JSON com MDC `traceId`,`comercioId`.
- GitHub Actions com **triggers por path**: mudança em `contracts/**` → roda tudo; em `drivers/driver-bling/**` → só o driver-bling; cache Gradle; relatório JaCoCo como artefato.

**Critérios de aceite.**
- `docker compose up -d` sobe Postgres; app conecta no perfil dev.
- Flyway aplica a baseline; app valida o schema sem erro.
- PR que toca só `drivers/driver-bling/**` dispara só o pipeline do driver.
- `GET /actuator/prometheus` expõe métricas; logs saem em JSON com `traceId`.

**Cenários de teste.**
- `Dado o container Postgres de teste / Quando a app sobe em test / Então Flyway aplica a baseline`.
- `Dado um PR que altera só o driver-bling / Quando o CI roda / Então só o job do driver-bling executa`.

**NÃO FAZER.** `ddl-auto=update/create`; deploy contínuo (vem no S7); segredos no YAML; montar Grafana/Prometheus no compose agora (só expor endpoints).

**Labels:** infra, postgres, flyway, ci, observability

---

### REC-004 · [TASK] · 🟠 HIGH · 5 SP · EP-04
**Base do `driver-sdk` + regras ArchUnit**

**Descrição.** Kit reaproveitável para escrever qualquer driver rapidamente, e as regras de arquitetura que impedem o vazamento core↔driver.

**Instruções de desenvolvimento.**
- `driver-sdk`: `ResilientErpClient` (WebClient + Resilience4j: retry/CB/rate limiter), `CanonicalPublisher` (publica eventos canônicos via outbox/broker), esqueleto de `WebhookReceiver` e `IncrementalPoller`, utilidades de idempotência (`IdempotencyKey`).
- ArchUnit (em `common` ou módulo de testes de arquitetura): `core-api` não importa `com.recorra.driver..`; nenhum driver importa domínio do core; a única ponte permitida é `com.recorra.contracts`.

**Critérios de aceite.**
- `driver-sdk` compila e é consumível por `driver-bling`.
- Regra ArchUnit falha o build se `core-api` importar um driver, ou vice-versa, fora do contrato.

**Cenários de teste.**
- `Dado as regras ArchUnit / Quando um teste tenta importar driver a partir do core / Então o build falha`.
- Teste do `ResilientErpClient` com WireMock: em 429, aplica backoff e respeita o rate limiter.

**NÃO FAZER.** Não colocar regra de negócio no sdk; não acoplar o sdk a um ERP específico.

**Labels:** driver-sdk, archunit, resilience

---
---

## Sprint 1 · Contrato Canônico, Tenant & IAM
*Fase: Core · Semanas 3–4 · ~24 SP*

🎯 **Meta:** contrato canônico v0 fechado; isolamento por comércio (RLS); IAM/RBAC; onboarding de comércio; descoberta técnica do Bling destravada.

### REC-019 · [SPIKE] · 🔴 CRITICAL · 3 SP · EP-04
**Descoberta da API do Bling (captura de vendas)**

**Descrição.** O portal público do Bling é insuficiente para fechar a estratégia de captura. Esta PoC confirma, junto ao suporte do Bling, os eventos de webhook de venda/nota, limites de taxa, escopos OAuth e o formato do payload de pedido — antes de implementar o driver.

**Instruções de desenvolvimento.**
- Abrir conta/sandbox do Bling; registrar app OAuth2; obter escopos de leitura (vendas, contatos, produtos, estoque).
- Testar: fluxo OAuth, `GET /pedidos/vendas` (+ itens), assinatura de webhook de venda, e capturar um payload real de pedido.
- Contatar o suporte do Bling para o que a doc não cobrir (eventos de webhook exatos, rate limits).
- Entregável: documento curto (`docs/bling-capture.md`) com a decisão webhook × polling e exemplos de payload para os testes do driver (WireMock).

**Critérios de aceite.**
- Decisão de captura documentada (webhook + polling de reconciliação).
- Payloads reais de pedido/venda salvos como fixtures.
- Escopos OAuth e limites confirmados.

**Cenários de teste.** N/A (spike). Saída = documento + fixtures que alimentam REC-021/REC-022.

**NÃO FAZER.** Não construir o driver aqui; não assumir formato de payload sem amostra real.

**Labels:** spike, bling, integração, descoberta

---

### REC-010 · [STORY] · 🔴 CRITICAL · 8 SP · EP-03
**Contrato canônico v0 (modelo + eventos + comando)**

**Descrição.** Fechar o contrato que torna o core agnóstico. É o que permite core e drivers evoluírem em paralelo.

**Instruções de desenvolvimento.**
- Em `contracts`, definir (como schema): `ClienteCanonico`, `ProdutoCanonico`, `EstoqueCanonico`, `VendaCanonica`+`ItemVendaCanonico`, `ComercioRef`.
- Eventos: `venda.canonica.registrada`, `cliente.canonico.upsert`, `produto.canonico.upsert`, `estoque.canonico.atualizado`, `driver.conexao.status`.
- Comando: `mensagem.enviar` (core → MessagingPort).
- Versionar o contrato com SemVer; documentar a política (breaking = major).
- Gerar classes e validar round-trip em core e driver.

**Critérios de aceite.**
- Todos os tipos/eventos canônicos gerados e compartilhados por core e driver.
- Nenhum campo específico de ERP no canônico.
- Versão `0.1.0` tagueada no contrato.

**Cenários de teste.**
- Round-trip JSON de cada evento (serializa → desserializa → igual).
- Teste de compatibilidade: core e driver compilam contra a mesma versão gerada.

**NÃO FAZER.** Vazar campo de Bling; marcar como major sem necessidade; colocar regra no contracts.

**Labels:** contracts, canonical, versionamento

---

### REC-011 · [STORY] · 🔴 CRITICAL · 8 SP · EP-02
**Multi-tenant (Conta/Comércio) + Row-Level Security**

**Descrição.** Isolamento físico de dados por comércio — invariante de negócio nº 4. Base de tudo que persiste.

**Instruções de desenvolvimento.**
- Migration `V0101__tenant.sql`: `conta`, `comercio` (`erp`, `erp_conta_ref`, `vertical`, `status`).
- `TenantContext` (ThreadLocal/ScopedValue) resolvido do claim `comercio_id` do JWT por um filtro.
- RLS: toda tabela de domínio terá `comercio_id NOT NULL`; policy `USING (comercio_id = current_setting('app.current_comercio')::uuid)`. Interceptar início de transação para `SET app.current_comercio`.
- A app conecta com role **não-superuser** (RLS vale).

**Critérios de aceite.**
- Com contexto = comércio A, nenhuma query retorna linha do comércio B, mesmo sem filtro no JPQL.
- Requisição de negócio sem tenant resolvido é rejeitada.

**Cenários de teste.**
- `Dado dados de A e B / Quando contexto é A / Então repositório retorna só A` (integração Testcontainers).
- `Dado ausência de comercio_id / Quando acessa recurso tenant-scoped / Então erro`.
- Tentativa de `SET` de outro comércio não burla a policy.

**NÃO FAZER.** Filtro de aplicação como substituto de RLS; schema-per-tenant agora (documentar como opção); rodar a app como superuser do Postgres.

**Labels:** tenancy, rls, postgres, security

---

### REC-012 · [STORY] · 🔴 CRITICAL · 5 SP · EP-02
**IAM — autenticação OIDC/JWT e RBAC**

**Descrição.** Autenticação e autorização do dashboard, delegadas a um IdP.

**Instruções de desenvolvimento.**
- Spring Security como **OAuth2 Resource Server** validando JWT (IdP: Keycloak ou Cognito — fixar um). JWK URI por env.
- Papéis: `SUPER_ADMIN` (operador da plataforma), `OWNER`, `MANAGER`, `SELLER`. Claims: `sub`, `comercio_id`, `roles`.
- `SecurityFilterChain`: `/api/v1/**` autenticado; `/actuator/health`, `/webhooks/**`, `/swagger-ui/**` conforme política; `@PreAuthorize` por papel. CSRF off (stateless).

**Critérios de aceite.**
- Rota restrita a OWNER → 403 para papel inferior.
- Token inválido/expirado → 401.
- `/webhooks/**` não exige JWT (usa HMAC próprio).

**Cenários de teste.**
- `Dado usuário MANAGER / Quando acessa rota OWNER / Então 403`.
- `Dado JWT expirado / Quando acessa rota protegida / Então 401`.
- `Dado rota pública / Quando acessa sem token / Então 200`.

**NÃO FAZER.** Auth própria com senha/hash (delegar ao IdP); proteger webhooks por JWT; segredos do IdP no repo.

**Labels:** iam, security, oidc, jwt

---

### REC-013 · [STORY] · 🟠 HIGH · 3 SP · EP-02
**Onboarding de comércio (provisionamento de tenant)**

**Descrição.** Criar um comércio, definindo seu ERP e vertical, e seus usuários.

**Instruções de desenvolvimento.**
- `POST /api/v1/comercios` (SUPER_ADMIN): cria `comercio` com `erp` e `vertical`, status `PENDENTE`.
- `GET /api/v1/comercios/{id}`; `PATCH /comercios/{id}/config` (janela N, flags). `POST/PATCH /users`.
- Estados: `PENDENTE → CONECTADO → ATIVO`. Erros RFC 7807.

**Critérios de aceite.**
- Comércio criado com config default e status `PENDENTE`.
- Papel não-admin não cria comércio.

**Cenários de teste.**
- `Dado SUPER_ADMIN / Quando cria comércio erp=bling / Então PENDENTE + config default`.
- `Dado OWNER / Quando tenta criar comércio / Então 403`.

**NÃO FAZER.** Iniciar sync aqui; hardcodar verticais; criar UI.

**Labels:** tenant, onboarding, config

---
---

## Sprint 2 · Ingestão Canônica & driver-bling
*Fase: Core + Driver · Semanas 5–6 · ~24 SP*

🎯 **Meta (marco crítico):** uma venda registrada no Bling aparece consolidada no core **sem o core saber que o Bling existe**. Valida a arquitetura Ports & Adapters ponta a ponta.

### REC-020 · [STORY] · 🔴 CRITICAL · 8 SP · EP-05
**Consumo de eventos canônicos e consolidação idempotente**

**Descrição.** O core recebe eventos canônicos (de qualquer driver) e materializa Cliente/Produto/Venda/Item na base consolidada, sem jamais saber a origem.

**Instruções de desenvolvimento.**
- Entidades canônicas no core: `cliente`, `produto`, `venda`, `item_venda` (migrations `V0201__*`), com `comercio_id` + RLS e uniques `erp_venda_id`/`erp_cliente_id`/`erp_produto_id` por comércio.
- Consumers de `venda.canonica.registrada`, `cliente.canonico.upsert`, `produto/estoque` (via broker; no S2 pode usar `ApplicationEventPublisher` como ponte se o broker vier só no S4 — abstrair a publicação). Upsert idempotente.
- Derivar `qualidade_cadastro` do cliente (mínimo/parcial/completo). Emitir `venda.persistida`.

**Critérios de aceite.**
- Evento `venda.canonica.registrada` → venda+itens+cliente consolidados e coerentes.
- Reprocessar o mesmo evento não duplica nem altera estado.
- O core não referencia nenhum tipo específico de ERP.

**Cenários de teste.**
- `Dado um evento de venda / Quando consumido / Então venda+itens+cliente persistem`.
- `Dado o mesmo evento 2x / Quando consumido / Então estado idêntico (idempotência)`.
- `Dado item com produto ainda não espelhado / Quando consumido / Então trata conforme política (cria stub ou rejeita com DLQ)`.

**NÃO FAZER.** Conhecer o ERP de origem; calcular recorrência aqui; escrever no ERP.

**Labels:** ingestion, canonical, idempotência

---

### REC-021 · [STORY] · 🔴 CRITICAL · 8 SP · EP-04
**driver-bling — conexão OAuth2 e tokens por comércio**

**Descrição.** O driver autentica cada comércio no Bling e mantém os tokens válidos, isolados e cifrados.

**Instruções de desenvolvimento.**
- `POST /driver/v1/bling/conectar/{comercioId}` → URL de autorização (state assinado). `GET /driver/v1/bling/callback` → troca code por tokens.
- Persistir access+refresh **cifrados por comércio** (Secrets Manager em prod; tabela cifrada em dev). `BlingTokenService.getValidAccessToken(comercioId)` com refresh automático. `TokenRefreshJob` horário.
- Usar `ResilientErpClient` do `driver-sdk`. Emitir `driver.conexao.status`.
- Testes com WireMock simulando o IdP do Bling.

**Critérios de aceite.**
- Fluxo OAuth conclui → tokens cifrados → status `CONECTADO`.
- Access token expirado → refresh transparente.

**Cenários de teste.**
- `Dado comércio PENDENTE / Quando conclui OAuth / Então tokens cifrados + CONECTADO`.
- `Dado access token expirado / Quando precisa chamar o Bling / Então usa refresh e obtém novo token`.
- Token nunca aparece em log (teste de appender).

**NÃO FAZER.** Logar tokens; guardar em texto puro; compartilhar token entre comércios; chamar endpoints de dados ainda.

**Labels:** driver-bling, oauth2, security

---

### REC-022 · [STORY] · 🔴 CRITICAL · 8 SP · EP-04
**driver-bling — captura (webhook + backfill + reconciliação) → canônico**

**Descrição.** Capturar vendas do Bling em tempo real e em lote, normalizar para canônico e publicar ao core. Usa a decisão/fixtures do REC-019.

**Instruções de desenvolvimento.**
- Webhook `POST /webhooks/bling` autenticado por **HMAC**; dedup por `erp_venda_id` (Redis/tabela). Responde 202, processa assíncrono.
- No evento: `BlingClient.obterPedido` → mapear (MapStruct) payload Bling → `VendaCanonica` → publicar `venda.canonica.registrada` via `CanonicalPublisher`.
- Backfill histórico: Spring Batch paginado, com cursor retomável. Polling incremental (`BlingIncrementalSyncJob`, 5–15 min) como rede de segurança, por cursor `ultima_sincronizacao`.
- Sync de produtos/estoque → `produto.canonico.upsert`/`estoque.canonico.atualizado`.

**Critérios de aceite.**
- Venda no Bling (webhook) → evento canônico publicado → core consolida.
- Webhook perdido é reconciliado pelo polling.
- Backfill importa histórico sem duplicar e é retomável.
- HMAC inválido → 401, nada processado.

**Cenários de teste.**
- `Dado um webhook válido do Bling / Quando recebido / Então publica venda.canonica.registrada e o core consolida` (e2e com WireMock Bling).
- `Dado HMAC inválido / Quando recebido / Então 401 e nada processado`.
- `Dado um webhook perdido / Quando o polling roda / Então a venda faltante é reconciliada`.
- `Dado backfill interrompido / Quando reexecuta / Então retoma do cursor sem duplicar`.
- Rate limit do Bling (429) respeitado no backfill.

**NÃO FAZER.** Vazar DTO do Bling para o core; processar síncrono no request do webhook; estourar rate limit; escrever no Bling.

**Labels:** driver-bling, webhook, backfill, mapstruct

> **✅ Marco do Sprint 2:** com REC-020/021/022, prova-se a arquitetura: dado uma venda no Bling, ela aparece no core consolidada, e o core não tem uma linha de código sobre Bling.

---
---

## Sprint 3 · Motor de Recorrência
*Fase: Core · Semanas 7–8 · ~20 SP*

🎯 **Meta:** a partir do histórico canônico, calcular recorrência/ciclo por cliente×produto e o perfil consolidado. Lógica pura e testável; recomputo reativo a cada venda. Ainda sem ações/mensagens.

### REC-030 · [TASK] · 🟠 HIGH · 3 SP · EP-06
**Entidades derivadas (PerfilConsumoCliente, CicloRecompra)**

**Descrição.** Estruturas que guardam o resultado da inteligência (ver Core de Negócio §1.4).

**Instruções de desenvolvimento.**
- Migration `V0301__inteligencia.sql`: `perfil_consumo_cliente` (ticket_medio, freq_media_dias, ultima_compra, segmento, ltv_estimado) e `ciclo_recompra` (cliente_id, produto_id, intervalo_medio_dias, proxima_prevista, confianca). `comercio_id` + RLS.
- Entidades/repositórios JPA.

**Critérios de aceite.** Migration aplica; uniques por (cliente,produto) em ciclo; campos default coerentes.

**Cenários de teste.** `Dado o schema / Quando persisto um ciclo / Então unicidade por (cliente,produto) impede duplicata`.

**NÃO FAZER.** Preencher `segmento` com regra agora (default `NOVO`); calcular nada aqui.

**Labels:** inteligência, schema

---

### REC-031 · [STORY] · 🔴 CRITICAL · 8 SP · EP-06
**RecurrenceCalculator — cálculo de ciclo (função pura)**

**Descrição.** O coração da inteligência. Dado o histórico canônico ordenado de um cliente, calcula o ciclo por produto. Deve ser **puro** (sem I/O), para ser trivialmente testável.

**Instruções de desenvolvimento.**
- Serviço `RecurrenceCalculator` recebe lista de compras (data, produto) e devolve `CicloRecompra` por produto: `intervalo_medio_dias` (mediana dos gaps — mais robusta), `ultima_compra`, `proxima_prevista = ultima + intervalo`, `confianca` (função do nº de ocorrências: <2 nula, 2 baixa, ≥3 utilizável).
- Persistência separada do cálculo (um `IntelligenceService` orquestra: lê histórico → chama calculator → grava).

**Critérios de aceite.**
- Compras ~40 dias → intervalo ≈ 40, proxima_prevista = ultima + 40.
- 1 compra → sem ciclo (confiança insuficiente).
- Cálculo não faz I/O.

**Cenários de teste.**
- `Dado compras do produto X em 01/01, 10/02, 22/03 / Então intervalo≈40 e confiança utilizável`.
- `Dado 1 única compra / Então não gera ciclo`.
- `Dado duas compras no mesmo dia / Então gap zero não quebra o cálculo`.
- `Dado um outlier (uma compra muito fora) / Então a mediana não é distorcida` (justifica mediana vs média).

**NÃO FAZER.** ML/previsão complexa no MVP; I/O dentro do cálculo; gerar ação aqui.

**Labels:** inteligência, recorrência, core-negócio

---

### REC-032 · [STORY] · 🟠 HIGH · 5 SP · EP-06
**Consolidação diária + recomputo reativo**

**Descrição.** Manter perfis/ciclos atualizados: em lote (diário) e reativamente (a cada venda).

**Instruções de desenvolvimento.**
- `DailyConsolidationJob` (Spring Batch): por comércio, recalcula ciclos/perfis dos clientes que compraram no dia. Idempotente.
- Consumer de `venda.persistida`: recomputa **só** o cliente da venda; emite `perfil.recalculado{clienteId}`.

**Critérios de aceite.**
- Nova venda → perfil/ciclo do cliente atualizados; `perfil.recalculado` emitido.
- Rodar o job 2x = mesmo resultado.

**Cenários de teste.**
- `Dado uma venda persistida / Quando o consumer roda / Então o perfil do cliente é atualizado e o evento emitido`.
- `Dado o job diário rodando 2x / Então resultado idêntico (idempotência)`.

**NÃO FAZER.** Recalcular a base inteira todo dia (só afetados); recomputar outros clientes no reativo; gerar ação.

**Labels:** inteligência, batch, eventos

---

### REC-034 · [TASK] · 🔵 MEDIUM · 3 SP · EP-06
**Endpoints de consulta (histórico, perfil, ciclos)**

**Instruções de desenvolvimento.** `GET /api/v1/clientes/{id}/historico|perfil|ciclos`, DTOs (não expor entidade JPA), paginação padrão.

**Critérios de aceite.** Retornam dados do cliente escopados ao comércio; 404 para id inexistente.

**Cenários de teste.** `Dado cliente com histórico / Quando GET /historico / Então lista paginada`; `Dado cliente de outro comércio / Então não retorna (RLS)`.

**NÃO FAZER.** Expor entidade JPA; vazar cross-tenant.

**Labels:** api, consulta

---
---

## Sprint 4 · Ações, Outbox & Broker
*Fase: Core · Semanas 9–10 · ~22 SP*

🎯 **Meta:** formalizar o broker (RabbitMQ) com outbox, gerar a ação de **reposição** na janela de recompra e expor a fila de ações com feedback. Sem envio real de WhatsApp.

### REC-040 · [STORY] · 🟠 HIGH · 5 SP · EP-06
**AçãoRecomendada + máquina de estados**

**Instruções de desenvolvimento.**
- Migration `V0401__acao.sql`: `acao_recomendada` (tipo, cliente_id, produto_id, disparar_em, status). Estados `PENDENTE → ENVIADA → (CONVERTIDA | DESCARTADA)`; transições válidas impostas em serviço.
- Unicidade para evitar ação duplicada por (cliente, produto, tipo, janela).

**Critérios de aceite.** Transição inválida é rejeitada; unicidade impede ação ativa duplicada.

**Cenários de teste.** `Dado ação PENDENTE / Quando marco ENVIADA → CONVERTIDA / Então ok`; `Dado ação CONVERTIDA / Quando tento voltar para PENDENTE / Então erro`.

**NÃO FAZER.** Cross-sell/reativação/VIP agora (só tipo `REPOSICAO`).

**Labels:** inteligência, ações, state-machine

---

### REC-041 · [STORY] · 🔴 CRITICAL · 8 SP · EP-06
**ActionGeneratorJob — reposição na janela**

**Descrição.** Regra central de negócio: gerar a ação de reposição no momento certo (ver §1.4), respeitando opt-in.

**Instruções de desenvolvimento.**
- Para cada `ciclo_recompra` com confiança suficiente e `proxima_prevista − N ≤ hoje ≤ proxima_prevista`, criar `acao_recomendada(REPOSICAO)` se não existir ativa. `N` vem da config do comércio.
- Job diário + reação a `perfil.recalculado`. Dedup. **Opt-in checado na geração** (não gera para quem não consentiu). Emitir `acao.gerada`.

**Critérios de aceite.**
- Cliente com proxima_prevista em 4 dias e N=5 → cria 1 ação REPOSICAO PENDENTE.
- Cliente sem opt-in → nenhuma ação.
- Não cria ação duplicada na mesma janela.

**Cenários de teste.**
- `Dado proxima_prevista em 4d e N=5 e opt-in / Então 1 ação PENDENTE`.
- `Dado cliente sem opt-in / Então nenhuma ação`.
- `Dado já existe ação ativa na janela / Então não cria outra`.
- `Dado ciclo de baixa confiança / Então não gera`.

**NÃO FAZER.** Enviar mensagem (S5); múltiplas ações por janela; gerar para inativo além do corte definido.

**Labels:** inteligência, reposição, core-negócio

---

### REC-042 · [STORY] · 🟠 HIGH · 8 SP · EP-01
**Outbox pattern + publisher**

**Descrição.** Entregar eventos ao broker sem *dual-write*.

**Instruções de desenvolvimento.**
- Migration `V0402__outbox.sql`. `OutboxService.record(evento)` grava na **mesma transação** do domínio. `OutboxPublisher` (poller) publica e marca enviado. Retrofit dos eventos-ponte dos S2/S3 para passarem pelo outbox.

**Critérios de aceite.**
- Evento gravado no outbox na transação do domínio; publisher entrega e marca enviado.
- Falha no broker → evento permanece pendente e é reenviado.

**Cenários de teste.**
- `Dado transação que grava no outbox / Quando commita / Então publisher entrega uma vez (at-least-once + consumidor idempotente)`.
- `Dado broker fora / Quando publisher tenta / Então evento fica pendente e reenvia depois`.

**NÃO FAZER.** Publicar no broker dentro da transação de negócio; deletar outbox antes de confirmar publish.

**Labels:** outbox, eventos, consistência

---

### REC-043 · [STORY] · 🟠 HIGH · 5 SP · EP-01
**RabbitMQ — exchanges, filas, DLQ**

**Instruções de desenvolvimento.** RabbitMQ no compose + Spring AMQP. Topic exchange; filas por evento (`venda.*`, `perfil.recalculado`, `acao.gerada`, `mensagem.*`) + DLX/DLQ; retry com backoff; `POST /admin/dlq/{fila}/reprocessar` (papel admin).

**Critérios de aceite.** Consumidor que falha além do limite → DLQ; reprocessamento via endpoint admin.

**Cenários de teste.** `Dado consumidor que falha N vezes / Então mensagem vai à DLQ`; `Dado mensagem na DLQ / Quando reprocesso / Então é consumida`.

**NÃO FAZER.** Kafka agora (documentar migração); perder mensagem sem DLQ; DLQ sem proteção de papel.

**Labels:** rabbitmq, mensageria, dlq

---

### REC-044 · [TASK] · 🔵 MEDIUM · 3 SP · EP-06
**Endpoint da fila de ações + feedback**

**Instruções de desenvolvimento.** `GET /api/v1/acoes` (filtros tipo/status/data), `GET /acoes/{id}`, `PATCH /acoes/{id}` (feedback `CONVERTIDA`/`DESCARTADA`, auditado).

**Critérios de aceite.** Feedback transita o estado e audita; não lista ações de outro comércio.

**Cenários de teste.** `Dado ação PENDENTE / Quando dono marca DESCARTADA / Então transita e audita`; transição inválida → erro.

**NÃO FAZER.** Transições inválidas; ver ações de outro comércio.

**Labels:** api, ações, feedback

---
---

## Sprint 5 · Mensageria & WhatsApp
*Fase: Core + Adapters · Semanas 11–12 · ~23 SP*

🎯 **Meta:** enviar a ação como mensagem via WhatsApp, por uma **porta** com dois adapters (oficial e terceiro), respeitando opt-in e janela de 24h; receber status de entrega. Fecha o loop reposição → mensagem.

### REC-050 · [STORY] · 🟠 HIGH · 5 SP · EP-07
**MessagingPort (porta) + comando `mensagem.enviar`**

**Instruções de desenvolvimento.** Interface `MessagingPort.enviar(MensagemCanonica)` no core; seleção do adapter por config do comércio (`messaging_provider`). Core só emite o comando canônico; não conhece WhatsApp.

**Critérios de aceite.** Core compila/roda sem depender de nenhum adapter concreto; adapter resolvido em runtime por config.

**Cenários de teste.** `Dado comércio com provider=oficial / Quando o core envia / Então o adapter oficial é invocado` (com adapters mockados).

**NÃO FAZER.** Core conhecer detalhe de WhatsApp; acoplar a um provider.

**Labels:** engajamento, porta, hexagonal

---

### REC-051 · [STORY] · 🔴 CRITICAL · 8 SP · EP-07
**Adapter wa-cloud (WhatsApp Cloud API oficial)**

**Instruções de desenvolvimento.** `messaging/wa-cloud`: `WhatsappClient` (WebClient) chamando Graph API `POST /{phone_number_id}/messages`; suporte a template (fora da janela 24h) e sessão (dentro). Credenciais por comércio cifradas. Registrar `message_id` e custo (~R$0,37/msg). WireMock nos testes.

**Critérios de aceite.** Fora da janela → usa template aprovado; registra message_id.

**Cenários de teste.** `Dado ação fora da janela 24h / Quando envia / Então usa template e registra message_id`; `Dado erro da Meta / Então marca falha e não quebra o fluxo`.

**NÃO FAZER.** Mensagem de sessão fora da janela; hardcodar template; chip não-oficial aqui.

**Labels:** whatsapp, cloud-api, adapter

---

### REC-052 · [STORY] · 🔵 MEDIUM · 5 SP · EP-07
**Adapter wa-terceiro (API alternativa + número dedicado)**

**Instruções de desenvolvimento.** `messaging/wa-terceiro`: envia aviso + link para o chat oficial da loja, via BSP/terceiro, por número dedicado. Mesma interface da porta. Documentar risco de bloqueio.

**Critérios de aceite.** Implementa `MessagingPort`; intercambiável com o oficial por config.

**Cenários de teste.** `Dado comércio com provider=terceiro / Quando envia / Então usa o adapter terceiro`; mensagem inclui link para o chat oficial.

**NÃO FAZER.** Prometer entrega oficial; ignorar o risco (documentar).

**Labels:** whatsapp, terceiro, adapter

---

### REC-053 · [STORY] · 🔴 CRITICAL · 5 SP · EP-07
**Opt-in/opt-out + consumer de `acao.gerada` → envio + status**

**Descrição.** Fecha o loop respeitando LGPD (§1.6).

**Instruções de desenvolvimento.**
- `POST/DELETE /api/v1/clientes/{id}/consentimento` (opt-in/opt-out com timestamp/origem). `ConsentPolicy.podeEnviar(cliente, tipo, agora)` (opt-in + janela).
- Consumer de `acao.gerada`: monta mensagem (template+variáveis) → `ConsentPolicy` → envia via `MessagingPort` → persiste `mensagem` (ENVIADA) → ação `ENVIADA`. Idempotente; throttle por número.
- Webhook `POST /webhooks/whatsapp` (verificação) → atualiza `mensagem` (entregue/lida/falha) → emite `mensagem.status`.

**Critérios de aceite.**
- Cliente com opt-in → mensagem enviada/registrada; ação vira ENVIADA.
- Cliente com opt-out → nada enviado.
- Reprocessar `acao.gerada` não reenvia.

**Cenários de teste.**
- `Dado opt-in / Quando acao.gerada / Então envia e registra, ação ENVIADA`.
- `Dado opt-out / Quando acao.gerada / Então não envia`.
- `Dado o mesmo evento 2x / Então não reenvia`.
- `Dado callback "entregue" / Então status da mensagem atualizado`.

**NÃO FAZER.** Enviar sem passar por ConsentPolicy; reenviar ação; disparar em massa sem throttle; aceitar callback sem verificação; criar bot/conversa (fora do MVP).

**Labels:** engajamento, lgpd, opt-in, webhook

---
---

## Sprint 6 · Dashboard, Higienização & Config
*Fase: Core · Semanas 13–14 · ~21 SP*

🎯 **Meta:** visibilidade para o dono (agregações), higienização progressiva da base e as regras configuráveis por vertical. Backend das telas; o app React consome esta API.

### REC-060 · [STORY] · 🟠 HIGH · 8 SP · EP-08
**Endpoints de dashboard (agregações)**

**Instruções de desenvolvimento.**
- `GET /api/v1/dashboard/resumo` (novos clientes/dia, ticket médio, vendas), `/recorrencia` (previsão de reposição), `/top-clientes` (valor e recorrência), `/oportunidades` (fila do dia), `/previsao-faturamento`, `/produtos` (mais vendidos × cliente × ciclo).
- Agregar no banco (queries/`views materializadas`); usar réplica de leitura quando existir.

**Critérios de aceite.** Métricas escopadas ao comércio; período parametrizável; sem varredura em memória por request.

**Cenários de teste.** `Dado dados consolidados / Quando GET /resumo?periodo / Então métricas corretas e escopadas`; `Dado dois comércios / Então nenhum vazamento (RLS)`.

**NÃO FAZER.** Calcular em memória varrendo tabela inteira; vazar cross-tenant.

**Labels:** dashboard, agregação, performance

---

### REC-061 · [STORY] · 🔵 MEDIUM · 5 SP · EP-08
**Higienização de base (DataQualityJob + fila de incompletos)**

**Instruções de desenvolvimento.** `DataQualityJob` semanal marca `qualidade_cadastro`; `GET /api/v1/clientes/incompletos`; emite evento de solicitação de enriquecimento (a coleta via WhatsApp é Fase 2 — só emitir o evento).

**Critérios de aceite.** Clientes só com primeiro nome marcados como mínimo e listados em `/incompletos`.

**Cenários de teste.** `Dado cliente só com nome / Quando o job roda / Então marcado mínimo e aparece em /incompletos`.

**NÃO FAZER.** Alterar/apagar dado automaticamente; implementar a coleta de carro/atributos agora.

**Labels:** higienização, qualidade-dados

---

### REC-062 · [STORY] · 🔵 MEDIUM · 5 SP · EP-08
**Regras por vertical (config)**

**Instruções de desenvolvimento.** `RegraVertical` (por comércio/vertical) dirige a janela de reposição `N` e, no futuro, a recomendação por atributo. O domínio lê config; não contém regra de vertical hardcoded.

**Critérios de aceite.** Alterar `N` por config muda a geração de ações sem deploy; nenhum `if (vertical == ...)` no domínio.

**Cenários de teste.** `Dado N=7 configurado / Quando o gerador roda / Então usa 7`; `Dado config ausente / Então usa default documentado`.

**NÃO FAZER.** Hardcodar regra de um vertical; misturar config com domínio.

**Labels:** config, vertical, regras

---
---

## Sprint 7 · Hardening, LGPD, Piloto & 2º Driver
*Fase: Qualidade · Semanas 15–16 · ~22 SP*

🎯 **Meta:** nível de produção (testes e2e, resiliência, LGPD), piloto no ar, e **a prova de que a arquitetura escala**: um segundo driver sem tocar no core.

### REC-070 · [STORY] · 🟠 HIGH · 8 SP · EP-09
**Suíte de integração ponta a ponta + gate de cobertura**

**Instruções de desenvolvimento.** Cenário e2e: webhook Bling (WireMock) → driver → canônico → core → recorrência → ação → WhatsApp (WireMock) → status. Testcontainers (Postgres, RabbitMQ, Redis). Ativar gate JaCoCo ≥80% no CI.

**Critérios de aceite.** Fluxo e2e verde no pipeline; gate de cobertura aplicado.

**Cenários de teste.** O próprio e2e é o cenário: `Dado uma venda no Bling / Então uma mensagem de reposição é enviada ao fim do fluxo`.

**NÃO FAZER.** Mockar o próprio domínio; baixar cobertura para passar.

**Labels:** qa, e2e, testcontainers

---

### REC-071 · [STORY] · 🔵 MEDIUM · 3 SP · EP-09
**Resiliência e reprocessamento**

**Instruções de desenvolvimento.** Revisar retries/CB de ERP/WhatsApp; DLQ + reprocessamento; alarmes de lag de sync/fila; testes de falha injetada.

**Critérios de aceite.** Falhas transitórias não derrubam o fluxo; itens vão à DLQ e reprocessam.

**Cenários de teste.** `Dado ERP intermitente / Quando sincroniza / Então recupera sem duplicar`.

**NÃO FAZER.** Silenciar exceção sem log/métrica; reprocessar sem idempotência.

**Labels:** resiliência, dlq, observabilidade

---

### REC-072 · [STORY] · 🟠 HIGH · 3 SP · EP-09
**LGPD ponta a ponta**

**Instruções de desenvolvimento.** Opt-out efetivo imediato; trilha de consentimento; PII cifrada em repouso e mascarada em logs; política de retenção + rotina/endpoint de exclusão a pedido; minimização (MVP só nome+WhatsApp+CPF opcional).

**Critérios de aceite.** Pedido de exclusão remove/anonimiza PII e cessa envios futuros.

**Cenários de teste.** `Dado pedido de exclusão / Quando processado / Então PII removida e nenhum envio futuro`; PII não aparece em log.

**NÃO FAZER.** Logar PII; reter além da política; usar PII no add-on de insights (só agregado/anonimizado).

**Labels:** lgpd, privacidade, segurança

---

### REC-073 · [SPIKE/STORY] · 🔴 CRITICAL · 8 SP · EP-04
**Prova do 2º driver (valida a abstração)**

**Descrição.** O teste definitivo da arquitetura: adicionar um segundo ERP **sem alterar o core**.

**Instruções de desenvolvimento.** Escrever um segundo driver — um ERP real simples **ou** um `driver-stub` que simula vendas — usando só o `driver-sdk` e o `contracts`. Publica eventos canônicos; o core consolida e gera ações exatamente como com o Bling.

**Critérios de aceite.**
- O segundo driver publica eventos canônicos e o core processa **sem nenhuma alteração em `core-api`**.
- Diff de `core-api` no PR = vazio.

**Cenários de teste.** `Dado o 2º driver conforme contrato / Quando publica vendas canônicas / Então o core consolida e gera reposição, sem mudança no core`.

**NÃO FAZER.** Alterar o core para acomodar o 2º ERP (se precisar, a abstração falhou — corrigir no contrato/sdk).

**Labels:** driver, arquitetura, prova-de-conceito

---

### REC-074 · [STORY] · 🟠 HIGH · 8 SP · EP-01
**Deploy do piloto (infra, segredos, flags, runbook)**

**Instruções de desenvolvimento.** Terraform (EKS/RDS/ElastiCache/Amazon MQ/Secrets Manager/S3); ArgoCD staging/prod; feature flags por comércio; runbook (onboarding, rollback, monitoramento).

**Critérios de aceite.** Versão em prod com health check e rollback; comércio-piloto onboardado **em paralelo** ao sistema atual do comerciante.

**Cenários de teste.** Smoke test pós-deploy; rollback testado em staging.

**NÃO FAZER.** Cortar o sistema atual do comerciante; expor segredos; liberar tudo sem flag.

**Labels:** devops, deploy, terraform, runbook

---
---

## Sprint 8+ · Fase 2 (backlog priorizável)
*Após o MVP validar core + 1 driver + prova do 2º.*

Cada item vira um épico próprio com o mesmo nível de detalhe quando priorizado:

- **REC-08x Cross-sell por margem** (EP-06) — ação `CROSS_SELL`; requer dado de margem confiável.
- **REC-08x Segmentação** profissional × consumidor final (EP-06).
- **REC-08x Enriquecimento de cadastro via WhatsApp** (EP-07) — fluxo conversacional.
- **REC-08x Reativação de inativos** (EP-06).
- **REC-08x Recomendação por regra de vertical** (EP-08) — config/plugin.
- **REC-08x Novos drivers** (Tiny, Omie, Linx…) — ~58 PF / ~1 sprint cada, sem tocar no core.
- **REC-08x App no marketplace do Bling** (EP-01) — distribuição/GTM.
- **REC-08x Dashboards avançados**, **multi-unidade/rede**, **white-label (B2B2B)**.

**NÃO FAZER sem repriorização:** novos drivers antes do MVP provar o core; recomendação com ML antes da recorrência simples se mostrar insuficiente.

---

## Apêndice — Resumo de dependências e SP

```
S0 (20) → S1 (24) → S2 (24) → S3 (20) → S4 (22) → S5 (23) → S6 (21) → S7 (22)   = ~176 SP (MVP)
```

Regra de ouro do desenvolvimento assistido: **um card = um PR pequeno e verde**; a seção `NÃO FAZER` é vinculante; **o core nunca aprende o nome de um ERP**.
