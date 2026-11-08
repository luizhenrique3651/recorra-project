# Recorra — Roadmap & Sprint Board (Spec-Driven Development)
### Plataforma de Inteligência de Recompra · Arquitetura driver-based · Monorepo

**Luiz Henrique Albuquerque Coelho** — Desenvolvedor Freelancer · Java / Spring Boot
*Novembro 2026 — Documento interno, uso exclusivo do desenvolvimento (humano + assistido por IA)*
*Revisão 2026-11-08: auditoria produto × plano técnico aplicada (`tecDoc/revisoes/2026-11-08-auditoria-produto-x-plano-tecnico.md`).*

> Codinome de trabalho: **Recorra** (provisório). Este documento é a fonte para o desenvolvimento assistido por IA (Gemini Pro / Claude Code) em regime **Spec-Driven Development**: cada card vira uma `spec.md`, depois `plan.md`, depois código. As seções **Instruções de Desenvolvimento**, **Cenários de Teste** e **NÃO FAZER** de cada card são vinculantes.

> **Marcadores desta revisão.** ⚠️ **DECISÃO PENDENTE DO USUÁRIO** = escolha que a auditoria deixou aberta; o card traz as opções e o padrão provisório (sempre a mais simples). ✅ **DECIDIDO (2026-11-08)** = decisão tomada pelo usuário (ADR-0004 Opção B, REC-015 Opção A, REC-043 Opção A e os defaults de negócio); o texto do card já reflete a decisão. 🆕 = card novo. ✂️ = card cortado ou movido. *Auditoria: Xn* = achado que motivou a mudança.

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
| **Comércio** | Unidade/loja. É o **tenant**: todo dado é isolado por `comercio_id`. Tem um `erp` e um `vertical`. É o **controlador** dos dados pessoais (LGPD); a plataforma é a **operadora**. |
| **ERP/PDV** | Sistema que o comércio já usa (ex.: Bling). Fonte da verdade transacional. A plataforma é **read-only** nele. |
| **Driver** | Adaptador (anti-corruption layer) de um ERP. Traduz o ERP para o **modelo canônico** e empurra eventos ao core. |
| **Modelo canônico** | Representação neutra de Cliente/Produto/Venda/Estoque, independente de qualquer ERP. O core só conhece isto. |
| **Cliente** | Comprador identificável (nome + WhatsApp no mínimo; CPF/e-mail opcionais). Venda sem cliente identificado não entra no motor (§1.4). |
| **Venda / ItemVenda** | Transação consolidada e seus itens, vinculada a um Cliente. Pode ser cancelada/devolvida no ERP (evento `venda.canonica.cancelada`). |
| **CicloRecompra** | Por cliente+produto: intervalo (mediana) entre compras e a próxima compra prevista. |
| **PerfilConsumoCliente** | Agregado por cliente: ticket médio, frequência, segmento, última compra. |
| **AçãoRecomendada** | "Próxima ação" gerada pela inteligência (reposição, cross-sell, reativação). Tem ciclo de vida (§1.4). |
| **Mensagem** | Comunicação enviada (via WhatsApp) referente a uma ação. |
| **Consentimento / ConsentPolicy** | Registro de opt-in/opt-out por `(comércio, telefone E.164)` (origem + timestamp) e a **política única** que decide se se pode enviar. Mora no core; usada pela geração de ação e pelo envio. |
| **RegraVertical** | Configuração por comércio/vertical (estende a janela de reposição, elegibilidade por categoria, regras de recomendação). |
| **MessagingPort** | Porta de saída para mensagens. No MVP, um único adapter: WhatsApp oficial (Meta Cloud API) — ADR-0003. |
| **Conversão** | Ação `CONVERTIDA`: nova venda do mesmo cliente+produto até X dias após a ação `ENVIADA` (atribuição automática, REC-063). |

### 1.4 A lógica de inteligência (o coração do negócio)

**Recorrência (por cliente × produto).** A partir do histórico consolidado, ordenado por data, calcula-se o intervalo entre compras consecutivas do mesmo produto. `intervalo_medio_dias` = **mediana** dos gaps (mais robusta a outliers; nome do campo mantido por compatibilidade). `proxima_prevista = ultima_compra + intervalo_medio_dias`.

*Regras fixadas (auditoria I2):*
- **Confiança:** 1 compra = sem ciclo; **2 compras (1 gap) = não gera ação**; **≥3 compras (≥2 gaps) = utilizável** e é o mínimo para gerar ação.
- **Compras no mesmo dia** (mesmo cliente e produto) contam como **uma única ocorrência**. Não existe gap zero.
- **Quantidade comprada não é considerada** (2 unidades ≠ ciclo dobrado). **Limitação do MVP**, documentada.
- **Venda cancelada ou devolvida** no ERP deixa de contar no histórico (evento canônico `venda.canonica.cancelada`).
- **Venda sem cliente identificado** (consumidor final) é ignorada pelo motor e contada numa métrica (REC-020).
- **"Hoje"** é a data no **fuso do comércio** (`fuso_horario` na config).

**Ação de reposição.** Para cada ciclo utilizável, quando `proxima_prevista − N ≤ hoje ≤ proxima_prevista + tolerância_de_atraso`, gera-se uma `AçãoRecomendada(REPOSICAO)` — **se** o cliente tiver opt-in (`ConsentPolicy`), o produto for **elegível à recompra**, o cliente não estiver **inativo** e não existir ação ativa idêntica.
- `N` (janela de antecedência, ex.: 5 dias num ciclo de 40) tem **fonte única**: a config do comércio (REC-013), com default documentado. A `RegraVertical` (REC-062) só a estende.
- **Atraso:** cliente atrasado (ou job parado por dias) ainda recebe a ação dentro da tolerância de atraso; além dela, não gera. **Inativo:** cliente sem compra há mais que o corte de inativo (REC-013) não gera ação (reativação é Fase 2).
- **Expiração (default decidido em 2026-11-08):** ação `PENDENTE` que passa de `proxima_prevista + tolerância_de_atraso` sem ser enviada vira `EXPIRADA`, inclusive quando o envio ficou bloqueado (template pendente, teto atingido). Nunca se envia uma reposição fora da janela.
- **Ação ativa idêntica:** mesma chave `(cliente, produto, tipo, janela)`, em que a janela é o ciclo vigente identificado pela data da `ultima_compra` (estável até nova compra).
- **Teto de frequência por cliente:** vários produtos vencendo na mesma semana não viram N mensagens; limita-se por cliente (REC-041) — **default decidido em 2026-11-08: LIMITAR** (consolidar vários produtos numa só mensagem vira Fase 2).

**Ciclo de vida da ação:** `PENDENTE → ENVIADA → (CONVERTIDA | DESCARTADA)`, mais `EXPIRADA` (o cliente recomprou antes do envio, ou a ação PENDENTE passou da tolerância de atraso), `CANCELADA` (opt-out, venda cancelada ou produto inelegível depois da geração) e `FALHA` (envio rejeitado ou "não entregue por política" da Meta, sem reenvio). Detalhe em REC-040.

**Segmentação (Fase 2).** Classifica o cliente por frequência/volume/mix: profissional (alta recorrência, LTV curto, sensível a preço — reter) vs. consumidor final/hobbysta (baixa recorrência, LTV longo — incentivar retorno) vs. novo/inativo.

**Cross-sell por margem (Fase 2).** Dado o que o cliente compra, sugerir produto equivalente de maior margem, ou complemento natural (quem compra X costuma precisar de Y).

**Recomendação por vertical (Fase 2/3).** Regra configurável por atributo do produto/cliente (ex.: produto adequado a um atributo do cliente). Entra por `RegraVertical`, nunca hardcoded.

### 1.5 Fluxo ponta a ponta

```
Venda no ERP → driver (captura + normaliza) → evento canônico → core (consolida)
→ motor de inteligência (recorrência/perfil) → geração de ação (ConsentPolicy) → MessagingPort → WhatsApp (Meta)
→ status de entrega → atribuição automática de conversão (nova venda) / feedback do dono → aprendizado
```

### 1.6 Regras de negócio transversais (invariantes)

1. **Read-only no ERP.** A plataforma nunca escreve venda/nota no ERP.
2. **Agnóstico de ERP.** O core nunca conhece um ERP concreto. Toda especificidade mora num driver. Proibido `if (erp == "bling")` no core.
3. **Idempotência.** Reprocessar um evento de venda não duplica nem corrompe estado (chave: `erp_venda_id` por comércio).
4. **Multi-tenant.** Todo dado é isolado por `comercio_id` (RLS). Nenhuma consulta cruza comércios.
5. **Consentimento (LGPD).** Nenhuma mensagem de marketing sem opt-in. Opt-out é imediato e definitivo até novo opt-in, e cancela as ações PENDENTES do cliente. **Uma única `ConsentPolicy`** decide; nenhuma outra classe reimplementa a regra.
6. **WhatsApp com regras da Meta e só pela API oficial.** Fora da janela de 24h, só template aprovado (regra do adapter, não do core). Sem API não oficial (ADR-0003).
7. **Minimização.** MVP coleta só nome + WhatsApp (+CPF/e-mail quando houver).
8. **Rastreabilidade e PII em log.** Todo log carrega `traceId`/`comercioId`; PII (telefone, nome, CPF, e-mail, tokens) é sempre mascarada; nunca se loga body de request/webhook (REC-005).

### 1.7 Glossário técnico rápido

ACL = Anti-Corruption Layer · Canônico = modelo neutro de domínio · Outbox = padrão para publicar evento junto à transação sem dual-write · RLS = Row-Level Security · DLQ = Dead-Letter Queue · Janela N = antecedência em dias do gatilho de reposição · E.164 = formato internacional de telefone (`+5592999999999`) · WABA = conta WhatsApp Business · BSP = provedor oficial credenciado da Meta.

---

## 2. Roadmap — Visão Geral

**10 sprints de 2 semanas no MVP (S0–S9)** (+ backlog de Fase 2). Regime parcial: ~14–18h/semana. Velocidade conservadora: **~20–24 SP/sprint**. O prazo total do MVP é de **~5 meses**; a faixa cobre o risco da integração com o ERP e com a Meta (dependências externas de prazo longo, ver §2.2).

> **Por que 10 sprints e não 8 (auditoria I7).** O plano original anunciava ~176 SP, mas os cards somavam 190 SP (os cabeçalhos de sprint estavam desatualizados). A auditoria acrescentou 49 SP de cards que faltavam e cortou/moveu/simplificou 20 SP (REC-052 −5, REC-073 −8, REC-012 −2, REC-074 −3, REC-043 −2) e o REC-013 subiu +2, ou seja, **corte LÍQUIDO de 18 SP (−20 e +2)**: o MVP passava a **221 SP**. *(Revisão das decisões de 2026-11-08: +5 SP do REC-016 e +2 SP do REC-043 → **228 SP**; o REC-015 concluído sai da conta → **227 SP**.)* A numeração dos sprints mudou e as dependências foram reordenadas (mapa abaixo). **Candidatos a corte se o prazo apertar (não aplicados):** REC-061 (higienização), parte de REC-060 (endpoints além de `/resumo` e `/oportunidades`), REC-064 opção React.

| Spr. | Nome | Fase | SP | Foco principal |
|---|---|---|---|---|
| **0** | Fundação do Monorepo | Infra | 25 | Monorepo Gradle · contracts (geração) · driver-sdk · Postgres/Flyway · CI por path · ArchUnit · **logging/rastreabilidade/PII** |
| **1** | Contrato Canônico, Tenant & IAM | Core | 28 | Contrato v0 · multi-tenant+RLS (jobs/admin) · IAM mínimo · spike Bling · ADR de PII · **componente de criptografia de PII (REC-016)** *(a decisão de montagem, REC-015, está concluída e fora da conta)* |
| **2** | Config do Comércio, Transporte, Ingestão & driver-stub | Core+Driver | 26 | Config única (janela N etc.) · **transporte driver→core (REC-043: HTTP autenticado + outbox do driver)** · consumo canônico idempotente · identidade do cliente · **driver-stub** |
| **3** | driver-bling & Consentimento | Driver+Core | 21 | OAuth Bling · captura (webhook/backfill) → canônico · **modelo de consentimento + `ConsentPolicy`** |
| **4** | Motor de Recorrência | Core | 22 | Cálculo de ciclo/perfil · consolidação diária · recomputo reativo · consultas · elegibilidade de produto |
| **5** | Ações & Outbox | Core | 21 | AçãoRecomendada (estados) · gerador de reposição · outbox (eventos internos) |
| **6** | Mensageria & WhatsApp | Core+Adapter | 23 | MessagingPort · **onboarding WhatsApp do comércio** · adapter oficial · **teto/custo de envio** |
| **7** | Envio, Opt-in & Conversão | Core | 21 | Consumer de envio + status · **captação de opt-in** · fila+feedback de ações · **atribuição automática de conversão** |
| **8** | Visibilidade, Higienização & Config | Core | 21 | Dashboards · **resumo semanal ou UI mínima** · higienização · regras por vertical |
| **9** | Hardening, LGPD & Piloto | Qualidade | 19 | Testes e2e · resiliência · LGPD · deploy simples do piloto |
| | **TOTAL MVP** | | **227 SP** | (232 se o REC-064 for a UI React) |

**Mapa de numeração (original → atual):** S0→S0 · S1→S1 · S2 (ingestão + driver-bling) → **S2 + S3** · S3 (motor) → **S4** · S4 (ações/outbox) → **S5** · S5 (mensageria) → **S6 + S7** (adapter, onboarding e teto no S6; consumer de envio, opt-in e conversão no S7) · S6 (dashboard) → **S8** · S7 (hardening/piloto) → **S9**. A "prova do 2º driver" (REC-073) saiu do MVP: o driver-stub entra no S2 como ferramenta e a prova formal vem depois do piloto.

### 2.1 Fases e Marcos

| Fase | Sprints | Marco de saída | Risco |
|---|---|---|---|
| Fundação | 0 | Monorepo buildando; contrato gerando código; CI por path; logs com PII mascarada | Baixo |
| Contrato & Base | 1 | Contrato v0 fechado; isolamento por comércio (RLS); IAM mínimo | Médio |
| Ingestão | 2–3 | Transporte driver→core decidido (S2); venda do **stub** (S2) e do **Bling** (S3) consolidada no core **sem o core conhecer Bling**; consentimento modelado | Alto (dep. Bling) |
| Inteligência | 4–5 | Recorrência calculada; ação de reposição gerada respeitando `ConsentPolicy` | Médio |
| Relacionamento | 6–8 | Mensagem no WhatsApp (API oficial) a partir da ação; base de opt-in; conversão medida; visibilidade para o dono | Alto (dep. Meta) |
| Prontidão | 9 | Piloto em produção em paralelo ao sistema atual do comerciante | Alto |

### 2.2 Frentes externas (fora do board — iniciar AGORA)

Não são código e têm **prazo longo**. Dependem do usuário. Cada uma trava um card; abrir já evita que o sprint pare esperando terceiros (auditoria C2, C3, I5).

| # | Frente | Responsável | Trava | O que fazer |
|---|---|---|---|---|
| **F1** | **Suporte do Bling** | Usuário | REC-019, REC-022 | Contatar o suporte já: eventos de webhook de venda/nota, rate limits, escopos OAuth, formato do payload de pedido. Não esperar o S1. |
| **F2** | **Onboarding da Meta** | Usuário | REC-051, REC-055, piloto | Iniciar Business verification; definir o WABA e o número do comércio-piloto; submeter o template de marketing de reposição para aprovação. |
| **F3** | **Validação dos dados do comerciante-candidato** | Usuário (+ dev analisa) | S2 (REC-020), escolha do piloto | Obter um export real de vendas do Bling de um candidato e medir: % de vendas com cliente identificado, % com telefone, nº de clientes com ≥3 compras do mesmo SKU. Se for baixo, rever o beachhead antes de investir no S2. |
| **F4** | **Papel LGPD e termo/DPA** | Usuário (+ jurídico) | Piloto (a ADR-0004 já está Aceita; o DPA segue pendente) | O comércio é o controlador e a plataforma, a operadora. Redigir e assinar o termo/DPA com o comerciante-piloto. |
| **F5** | **Beachhead e comerciante-piloto** | Usuário | S2 em diante | ⚠️ **DECISÃO PENDENTE DO USUÁRIO:** escolher vertical, ERP de praia e comerciante-piloto (informado por F3). |

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

- Código na branch correta (`feature/REC-XXX-descricao` → `develop`; `main` recebe só releases a partir de `develop`).
- Critérios de aceite verificados.
- **Cenários de teste do card implementados** (unit + integração) e verdes.
- Cobertura ≥ 80% de linha no módulo tocado (JaCoCo). O gate roda no `check` desde o S0.
- Sem violação de ArchUnit (fronteiras core↔driver↔contrato).
- Sem violação de lint/format (Spotless) nem SpotBugs crítico.
- Endpoints documentados (springdoc/OpenAPI).
- **Eventos de fronteira (os trocados entre core e driver) refletidos no `contracts`** (schema + AsyncAPI). Eventos **internos** do core (`venda.persistida`, `perfil.recalculado`, `acao.gerada`, `mensagem.status` etc.) **não** entram no `contracts`: ficam documentados na `spec.md` do card. *(Auditoria I4d)*
- Sem segredo hardcoded; **PII não logada** (mascaramento do REC-005 ativo; nenhum log de body de request/webhook).
- Migration Flyway para toda mudança de schema.
- Testes de integração HTTP com `RestTestClient` (ADR-0002), não `java.net.http.HttpClient`/`TestRestTemplate`.
- PR mergeado em `develop` sem conflito; migration aplicada em dev.

### 3.4 Branches e commits

Modelo: `main` (releases) + `develop` (integração) + `feature/REC-XXX-descricao`.

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
| **EP-01** | Fundação & DevOps | Monorepo, contracts/geração, driver-sdk, Postgres/Flyway, CI/CD por path, **logging/rastreabilidade/PII**, ArchUnit, outbox/transporte, deploy simples | 0, 5, 9 |
| **EP-02** | Tenancy & IAM | Conta/Comércio, RLS (inclui jobs/admin), OIDC/JWT, RBAC mínimo (SUPER_ADMIN + OWNER), onboarding, **config única do comércio**, ADR de PII | 1, 2 |
| **EP-03** | Contrato Canônico | Modelo + eventos canônicos (inclui cancelamento), versionamento, geração de código | 0, 1 |
| **EP-04** | Drivers (ACL de ERP) | driver-bling (auth + captura + normalização), **driver-stub**, driver-sdk. A prova formal do 2º driver (REC-073) fica na Fase 2 | 1, 2, 3 |
| **EP-05** | Ingestão & Catálogo | Consumo canônico, consolidação idempotente, **identidade do cliente (E.164/dedup)**, espelho de catálogo/estoque | 2 |
| **EP-06** | Inteligência | Recorrência, perfil, **elegibilidade de produto**, geração de ações, ciclo de vida da ação | 4, 5, 7 |
| **EP-07** | Engajamento | MessagingPort, adapter WhatsApp oficial, **consentimento/ConsentPolicy**, **captação de opt-in**, **onboarding WhatsApp do comércio**, envio, status, **teto/custo** | 3, 6, 7 |
| **EP-08** | Dashboard & Config | Agregações, **atribuição automática de conversão**, **resumo semanal ou UI mínima**, higienização, regras por vertical | 7, 8 |
| **EP-09** | Qualidade & LGPD | Testes e2e, resiliência, LGPD (retenção/exclusão), piloto | 9 |

---
---

## Sprint 0 · Fundação do Monorepo
*Fase: Infraestrutura · Semanas 1–2 · 25 SP*

🎯 **Meta:** monorepo multi-módulo buildando; `contracts` gerando classes a partir do schema; `driver-sdk` base; Postgres/Flyway; CI por path; **logging com rastreabilidade e PII mascarada**; ArchUnit impondo fronteiras. Sem regra de negócio.

> Sprint 0 fica ~1–5 SP acima da velocidade conservadora por causa do REC-005. Se estourar, o REC-005 não se divide: ele precisa estar completo antes de qualquer PII entrar (S2).

### REC-001 · [TASK] · 🔴 CRITICAL · 5 SP · EP-01
**Bootstrap do monorepo Gradle multi-módulo**

*Auditoria: D1, C5. Estado: concluído — 6 módulos, build verificado com JDK 21.*

**Descrição.** Criar o repositório único com os módulos definitivos e cada serviço Spring Boot subindo com health check. É a fundação sobre a qual todo o resto é construído.

**Instruções de desenvolvimento.**
- Gradle 9 (Kotlin DSL). `settings.gradle.kts` incluindo: `contracts`, `common`, `core-api`, `drivers:driver-sdk`, `drivers:driver-bling`, `messaging:wa-cloud`. **Remover o módulo `messaging:wa-terceiro`** do bootstrap (fora do MVP, ADR-0003): tirar o `include` do `settings.gradle.kts`, apagar o diretório `messaging/wa-terceiro/` e remover a linha dele na tabela de módulos do `README.md`. Marcar o item 3 da ADR-0003 como `[x]` no PR deste card. **Este card é o dono dessa remoção.** O `drivers:driver-stub` entra no REC-023.
- `build-logic/` com convention plugins: Java 21 toolchain, Spotless, JaCoCo (gate ≥80% no `check`), SpotBugs, versões centralizadas (version catalog `libs.versions.toml`).
- `core-api` e `driver-bling` são apps **Spring Boot 4.1.x** (ADR-0002) com **`spring-boot-starter-webmvc`**, `-actuator` e **springdoc 3.x**. Demais módulos são libs (`java-library`). **Bump para Boot 4.2 quando sair o GA** (previsto nov/2026): alterar só `spring-boot` no version catalog.
- Pacotes: `com.recorra.core.*`, `com.recorra.driver.bling`, `com.recorra.contracts`, `com.recorra.common`.
- Ligar virtual threads (`spring.threads.virtual.enabled=true`).

**Critérios de aceite.**
- `./gradlew build` compila todos os módulos sem erro (rodado de fato, com JDK 21).
- `core-api` e `driver-bling` sobem e respondem `GET /actuator/health` → 200 com `status` = `UP`.
- `./gradlew :core-api:bootRun` sobe só o core.
- README com "como rodar".
- `settings.gradle.kts` com **6 módulos** (`contracts`, `common`, `core-api`, `driver-sdk`, `driver-bling`, `wa-cloud`); `messaging:wa-terceiro` não existe mais no settings, no diretório `messaging/wa-terceiro/` nem no README; o build continua verde.

**Cenários de teste.**
- `Dado o monorepo / Quando rodo o build / Então todos os módulos compilam`.
- `Dado core-api no ar / Quando GET /actuator/health / Então 200 UP`.
- Smoke test de contexto Spring (`@SpringBootTest` mínimo) em core-api e driver-bling (a padronização para `RestTestClient` é feita no REC-003).

**NÃO FAZER.** Não criar domínio/endpoints de negócio; não acoplar módulos entre si; não colocar tudo num módulo só; não usar `spring-boot-starter-web` nem Boot 3.x; não reintroduzir `wa-terceiro`.

**Labels:** infra, gradle, monorepo, setup

---

### REC-002 · [TASK] · 🔴 CRITICAL · 5 SP · EP-03
**Esqueleto do `contracts` com geração de código a partir de schema**

*Auditoria: D3. Reescrito conforme a ADR-0001 (JSON Schema como fonte única; um só gerador).*

**Descrição.** O contrato canônico é a fonte da verdade sobre o fio entre core e drivers. Deve ser definido como **JSON Schema** e as classes Java **geradas** no build — nunca escritas à mão.

**Instruções de desenvolvimento.** (layout da ADR-0001)
- Fonte da verdade: `contracts/src/main/schemas/` (`modelo/`, `eventos/`, `comandos/`), um arquivo `.schema.json` (draft-07) por tipo.
- `contracts/src/main/asyncapi/recorra.asyncapi.yaml` (AsyncAPI 3.0: canais e mensagens) e `contracts/src/main/openapi/ingestao.openapi.yaml` (OpenAPI 3.1: ingestão síncrona) **referenciam** os schemas por `$ref` e **não redefinem** formatos.
- Geração: **somente `jsonschema2pojo`**, no build do módulo `contracts`; saída em `build/generated/sources/jsonschema2pojo/` adicionada ao sourceSet (não versionada). Provar no Gradle 9: plugin oficial ou, se não funcionar, task própria no `build-logic` chamando o `jsonschema2pojo-core`. O `openapi-generator` fica só como plano B documentado.
- Jackson 3 (Boot 4): evitar `uniqueItems` e `additionalProperties` no schema; o teste de round-trip roda com o `ObjectMapper` do Boot 4.
- Definir **um** evento de exemplo end-to-end: `venda.canonica.registrada` com um payload mínimo, só para validar o pipeline de geração.
- Publicar `contracts` como dependência interna consumível por `core-api` e `drivers:*`.
- **Gate do JaCoCo sem vacuidade (débito do REC-001, obrigatório; registrar no `spec.md`):** (a) excluir `build/generated/**` (classes do `jsonschema2pojo`) da base do gate e do relatório; (b) o `recorra.java-conventions` deve **falhar**, e não pular, quando o módulo tem classes de produção e nenhum dado de execução (sem teste). Provar no PR com teste.

**Critérios de aceite.**
- `./gradlew :contracts:build` gera as classes Java a partir do schema.
- `core-api` e `driver-bling` importam e compilam contra os tipos gerados.
- Nenhum DTO canônico escrito à mão no `contracts`.

**Cenários de teste.**
- `Dado o schema de venda.canonica.registrada / Quando buildo contracts / Então a classe gerada existe e é serializável (round-trip JSON)`.
- Teste de serialização/desserialização Jackson 3 do evento gerado.

**NÃO FAZER.** Não escrever POJOs canônicos manualmente; não colocar lógica no módulo contracts; não incluir campos específicos de Bling; não usar dois geradores; não redefinir formato em AsyncAPI/OpenAPI.

**Labels:** contracts, codegen, jsonschema, asyncapi, openapi

---

### REC-003 · [TASK] · 🔴 CRITICAL · 5 SP · EP-01
**PostgreSQL + Flyway + Testcontainers + métricas + CI por path**

*Auditoria: C4 (logging saiu para o REC-005), D7 (RestTestClient), ADR-0001 item 3 (validação de schemas no CI).*

**Descrição.** Infra de persistência, baseline de schema, base de testes de integração e pipeline que só rebuilda o que mudou.

**Instruções de desenvolvimento.**
- `docker-compose.yml` com Postgres 16. Datasource por perfil (`dev`/`test`/`prod`), `ddl-auto=validate`.
- Flyway `V0001__baseline.sql` (extensão `pgcrypto` p/ `gen_random_uuid()`); convenção: PK uuid, `criado_em`/`atualizado_em` com default `now()`.
- **Convenção de versão de migration: `V<sprint><seq>__descricao.sql`** (sprint com 2 dígitos + sequência com 2 dígitos, ex.: S3 → `V0301`, `V0302`; baseline = `V0001`), numerada pela **ordem de execução**: o Flyway não aplica versão menor depois de uma maior (`validate` falha sem `outOfOrder`, que fica **proibido**). A numeração vale por app (cada app com migrations próprias, como o `driver-bling`, tem a sua sequência). Um teste/CI falha se houver versão duplicada.
- Classe base de teste com Testcontainers 2.x (Postgres reutilizável).
- **Padronizar os testes HTTP/smoke em `RestTestClient`** (ADR-0002), substituindo o `java.net.http.HttpClient` hoje usado nos smoke tests do REC-001.
- Métricas: Micrometer + `/actuator/prometheus`. (Logs, traceId e PII: REC-005.)
- GitHub Actions com **triggers por path**: mudança em `contracts/**` → roda tudo; em `drivers/driver-bling/**` → só o driver-bling; cache Gradle; relatório JaCoCo como artefato.
- No CI: validar os schemas contra o meta-schema e as fixtures contra os schemas (ex.: `networknt/json-schema-validator`; ADR-0001).
- **Débitos do REC-001 (rev-warn/tech lead, obrigatórios):** (a) no perfil `prod`, `springdoc.api-docs.enabled=false` e `springdoc.swagger-ui.enabled=false` (não expor o contrato em produção; reverificar no REC-074); (b) no `recorra.java-conventions`, carregar o Mockito como `-javaagent` no `tasks.test` (recomendação do Mockito 5), eliminando o aviso de agente dinâmico do JDK.

**Critérios de aceite.**
- `docker compose up -d` sobe Postgres; app conecta no perfil dev.
- Flyway aplica a baseline; app valida o schema sem erro.
- PR que toca só `drivers/driver-bling/**` dispara só o pipeline do driver.
- `GET /actuator/prometheus` expõe métricas.
- Smoke tests usam `RestTestClient`.

**Cenários de teste.**
- `Dado duas migrations com a mesma versão / Quando o CI valida / Então falha`.
- `Dado o container Postgres de teste / Quando a app sobe em test / Então Flyway aplica a baseline`.
- `Dado um PR que altera só o driver-bling / Quando o CI roda / Então só o job do driver-bling executa`.
- `Dado um schema inválido ou fixture fora do schema / Quando o CI valida / Então o job falha`.

**NÃO FAZER.** `ddl-auto=update/create`; deploy contínuo (vem no S9); segredos no YAML; montar Grafana/Prometheus no compose agora (só expor endpoints); `TestRestTemplate`.

**Labels:** infra, postgres, flyway, ci, testcontainers

---

### REC-004 · [TASK] · 🟠 HIGH · 5 SP · EP-04
**Base do `driver-sdk` + regras ArchUnit**

**Descrição.** Kit reaproveitável para escrever qualquer driver rapidamente, e as regras de arquitetura que impedem o vazamento core↔driver.

**Instruções de desenvolvimento.**
- `driver-sdk`: `ResilientErpClient` (WebClient + Resilience4j: circuit breaker e rate limiter; retry com `@Retryable` do Spring 7 ou Resilience4j — confirmar a versão compatível com o Boot 4, ADR-0002), `CanonicalPublisher` (interface que publica eventos canônicos; **o transporte é plugável**; no MVP é o HTTP de ingestão idempotente, decisão do REC-043), esqueleto de `WebhookReceiver` e `IncrementalPoller`, utilidades de idempotência (`IdempotencyKey`). O `CanonicalPublisher` propaga o `traceparent` (REC-005).
- ArchUnit (em `common` ou módulo de testes de arquitetura): `core-api` não importa `com.recorra.driver..`; nenhum driver importa domínio do core; a única ponte permitida é `com.recorra.contracts`.

**Critérios de aceite.**
- `driver-sdk` compila e é consumível por `driver-bling`.
- Regra ArchUnit falha o build se `core-api` importar um driver, ou vice-versa, fora do contrato.

**Cenários de teste.**
- `Dado as regras ArchUnit / Quando um teste tenta importar driver a partir do core / Então o build falha`.
- Teste do `ResilientErpClient` com WireMock: em 429, aplica backoff e respeita o rate limiter.

**NÃO FAZER.** Não colocar regra de negócio no sdk; não acoplar o sdk a um ERP específico; não acoplar o `CanonicalPublisher` a um transporte específico.

**Labels:** driver-sdk, archunit, resilience

---

### REC-005 · 🆕 [TASK] · 🔴 CRITICAL · 5 SP · EP-01
**Logging, rastreabilidade e mascaramento de PII (obrigatório desde o S0)**

*Auditoria: C4. Absorve o item "Logback JSON com MDC" que estava no REC-003. O REC-072 passa a **verificar** o mascaramento, não a introduzi-lo.*

**Descrição.** A PII entra no S2; o mascaramento não pode esperar o S9. Este card entrega a base de logs do monorepo: estruturados, rastreáveis de ponta a ponta (driver → transporte/outbox → core → WhatsApp) e sem PII.

**Instruções de desenvolvimento.**
- Logs em JSON (Logback) com `traceId`, `spanId` e `comercioId` (MDC) em `core-api`, `driver-bling` e demais apps.
- Propagação do **W3C `traceparent`** em HTTP (entrada e saída) e nos **headers de mensagem** e no **outbox**: o `traceId` é gravado na linha do outbox (coluna criada no REC-042) e restaurado no consumidor. Na S0, entregar a infra reutilizável em `common` (filtro HTTP, utilitário de header, decorador do `CanonicalPublisher`).
- **Conversor de mascaramento de PII** no appender: telefone, nome, CPF, e-mail e tokens/segredos (access/refresh, `Authorization`, HMAC), **além da chave mestra, das DEKs e dos keysets AEAD/MAC (ADR-0004)**. **Teste de appender** que falha se algum desses valores aparecer em claro.
- **Contexto por unidade de trabalho:** todo job agendado, consumer de evento e handler de webhook, **para cada comércio processado**, abre o MDC `comercioId` + `traceId` (**novo** no job; **restaurado** do header/outbox no consumer) e **limpa o MDC ao final** (try/finally), inclusive em exceção. Utilitário em `common` usado pelo REC-011.
- **Query string de rotas OAuth:** o access log e o log de request **omitem ou mascaram** os parâmetros `code`, `state` e `token` (ex.: `GET /driver/v1/bling/callback?code=***&state=***`). Teste de appender.
- **Política de níveis:** stacktrace **uma única vez**, em `ERROR`, na borda (handler global/consumer); `WARN` **sem stacktrace** para rejeição de negócio; **nunca** logar body de request nem de webhook.
- **Handler global RFC 7807** (`ProblemDetail`) com o `traceId` na resposta; erro inesperado vira 500 genérico, sem detalhe interno.

**Critérios de aceite.**
- Toda linha de log em JSON traz `traceId`; `comercioId` quando há tenant resolvido.
- Chamada com `traceparent` de entrada mantém o mesmo `traceId` nos logs e na resposta de erro.
- Valores de PII e de token não aparecem em claro em nenhum log (teste de appender verde).
- Logs de job/consumer carregam o `comercioId` da unidade de trabalho e nenhum vaza o de outra; o MDC é limpo ao final.
- `code`/`state`/`token` de rotas OAuth não aparecem em claro em log algum.
- Exceção de negócio gera `WARN` sem stacktrace; exceção inesperada gera um único `ERROR` com stacktrace.

**Cenários de teste.**
- `Dado um log com telefone, nome, CPF, e-mail e token / Quando o appender escreve / Então todos saem mascarados`.
- `Dado uma requisição com traceparent / Quando processada e falha / Então o ProblemDetail traz o mesmo traceId e o log tem esse traceId`.
- `Dado uma rejeição de negócio / Então WARN sem stacktrace; Dado exceção inesperada / Então um único ERROR com stacktrace`.
- `Dado um request ou webhook com body / Então o body não aparece em log`.
- `Dado um job que processa os comércios A e B / Quando roda / Então os logs de A têm só comercioId=A, os de B só comercioId=B, e o MDC está vazio ao final`.
- `Dado um consumer com traceparent no header/outbox / Quando processa / Então o traceId do log é o restaurado, e é limpo ao final`.
- `Dado GET /callback?code=abc&state=xyz / Quando logado / Então code e state saem mascarados ou omitidos`.

**NÃO FAZER.** Logar body de request/webhook; stacktrace em `WARN` ou repetido a cada camada; adiar o mascaramento para o S9; montar stack de tracing/Grafana agora (só os headers e o MDC); logar PII "só em dev".

**Labels:** observabilidade, logging, pii, lgpd, rastreabilidade

---
---

## Sprint 1 · Contrato Canônico, Tenant & IAM
*Fase: Core · Semanas 3–4 · 28 SP (REC-015 concluído, fora da conta)*

🎯 **Meta:** contrato canônico v0 fechado; isolamento por comércio (RLS, inclusive para jobs, consumers e admin); IAM mínimo; descoberta técnica do Bling; decisões de PII e de montagem registradas.

### REC-019 · [SPIKE] · 🔴 CRITICAL · 3 SP · EP-04
**Descoberta da API do Bling (captura de vendas)**

*Auditoria: C3a. O contato com o suporte do Bling é a **frente externa F1**, aberta antes do sprint; este card consome o resultado.*

**Descrição.** O portal público do Bling é insuficiente para fechar a estratégia de captura. Esta PoC confirma os eventos de webhook de venda/nota, limites de taxa, escopos OAuth e o formato do payload de pedido — antes de implementar o driver. Se o suporte (F1) ainda não respondeu, o spike registra o que ficou em aberto e o REC-022 não começa sem resposta sobre webhook e rate limit.

**Instruções de desenvolvimento.**
- Abrir conta/sandbox do Bling; registrar app OAuth2; obter escopos de leitura (vendas, contatos, produtos, estoque).
- Testar: fluxo OAuth, `GET /pedidos/vendas` (+ itens), assinatura de webhook de venda, e capturar um payload real de pedido (inclusive venda **sem contato identificado**, venda **cancelada** e telefone em formatos variados).
- Consolidar a resposta do suporte do Bling (F1) para o que a doc não cobrir (eventos de webhook exatos, rate limits).
- Entregável: documento curto (`docs/bling-capture.md`) com a decisão webhook × polling e exemplos de payload para os testes do driver (WireMock).

**Critérios de aceite.**
- Decisão de captura documentada (webhook + polling de reconciliação).
- Payloads reais de pedido/venda salvos como fixtures (incluindo sem cliente e cancelada).
- Escopos OAuth e limites confirmados.

**Cenários de teste.** N/A (spike). Saída = documento + fixtures que alimentam REC-021/REC-022.

**NÃO FAZER.** Não construir o driver aqui; não assumir formato de payload sem amostra real.

**Labels:** spike, bling, integração, descoberta

---

### REC-010 · [STORY] · 🔴 CRITICAL · 8 SP · EP-03
**Contrato canônico v0 (modelo + eventos)**

*Auditoria: I2 (evento de cancelamento), I4c (comando `mensagem.enviar` fora do contrato, REC-015 Opção A decidida em 2026-11-08), C2 (venda sem cliente).*

**Descrição.** Fechar o contrato que torna o core agnóstico. É o que permite core e drivers evoluírem em paralelo.

**Instruções de desenvolvimento.**
- Em `contracts`, definir (como JSON Schema): `ClienteCanonico`, `ProdutoCanonico`, `EstoqueCanonico`, `VendaCanonica`+`ItemVendaCanonico`, `ComercioRef`.
- `VendaCanonica.cliente` é **opcional** (venda sem cliente identificado é válida no contrato; a política está no REC-020). `ClienteCanonico.telefone` é uma **string como veio do ERP** (a normalização E.164 é do core, REC-024).
- Eventos: `venda.canonica.registrada`, **`venda.canonica.cancelada`** (venda cancelada ou devolvida no ERP), `cliente.canonico.upsert`, `produto.canonico.upsert`, `estoque.canonico.atualizado`, `driver.conexao.status`.
- Comando `mensagem.enviar` (core → MessagingPort): **fica FORA do contrato** (decisão do REC-015, Opção A, 2026-11-08: o `MessagingPort` é uma interface in-process; não há segundo consumidor do comando).
- Versionar o contrato com SemVer; documentar a política (breaking = major) em `contracts/README.md`.
- Gerar classes e validar round-trip em core e driver. Taguear `contracts-v0.1.0`.

**Critérios de aceite.**
- Todos os tipos/eventos canônicos gerados e compartilhados por core e driver.
- Nenhum campo específico de ERP no canônico.
- Versão `0.1.0` tagueada no contrato.

**Cenários de teste.**
- Round-trip JSON de cada evento (serializa → desserializa → igual).
- Teste de compatibilidade: core e driver compilam contra a mesma versão gerada.
- `Dado uma venda sem cliente / Então o schema a aceita`; `Dado venda.canonica.cancelada / Então round-trip ok`.

**NÃO FAZER.** Vazar campo de Bling; marcar como major sem necessidade; colocar regra no contracts; pôr eventos internos do core no contrato (DoD §3.3).

**Labels:** contracts, canonical, versionamento

---

### REC-011 · [STORY] · 🔴 CRITICAL · 8 SP · EP-02
**Multi-tenant (Conta/Comércio) + Row-Level Security**

*Auditoria: I4a. Estendido: tenant fora do JWT (consumers e jobs) e role/policy para jobs e SUPER_ADMIN.*

**Descrição.** Isolamento físico de dados por comércio — invariante de negócio nº 4. Base de tudo que persiste.

**Instruções de desenvolvimento.**
- Migration `V0101__tenant.sql`: `conta`, `comercio` (`erp`, `erp_conta_ref`, `vertical`, `status`).
- `TenantContext` (ThreadLocal/ScopedValue). **Três origens do tenant:**
  1. **Requisição HTTP:** claim `comercio_id` do JWT, por um filtro.
  2. **Consumer de evento:** `comercio_id` do **próprio evento** (validado: o comércio existe e está ativo). Evento sem `comercio_id` é rejeitado.
  3. **Job agendado** (`DailyConsolidationJob`, `ActionGeneratorJob` etc.): o job enumera os comércios e, **para cada um**, abre o contexto e roda a unidade de trabalho numa transação própria. Nunca processa dois comércios na mesma transação. Cada unidade de trabalho abre o MDC `comercioId` + `traceId` novo e o limpa ao final (utilitário do REC-005).
- RLS: toda tabela de domínio terá `comercio_id NOT NULL`; policy `USING (comercio_id = current_setting('app.current_comercio')::uuid)`. Interceptar início de transação para `SET LOCAL app.current_comercio`.
- **Role/policy para jobs e admin** (detalhar na `spec.md`): uma role de banco não-superuser e **sem `BYPASSRLS`** para jobs, com policy que permite **somente a enumeração** de `comercio` (id/status) para o job iterar. O `SUPER_ADMIN` opera em `conta`/`comercio` (onboarding, listagem entre tenants) por policy própria, **nunca** lê tabela de domínio de outro tenant sem contexto.
- A app conecta com role **não-superuser** (RLS vale).

**Critérios de aceite.**
- Com contexto = comércio A, nenhuma query retorna linha do comércio B, mesmo sem filtro no JPQL.
- Requisição de negócio sem tenant resolvido é rejeitada; consumer com evento sem `comercio_id` é rejeitado.
- Um job consegue listar comércios e processar cada um isolado; a enumeração não expõe dado de domínio.

**Cenários de teste.**
- `Dado dados de A e B / Quando contexto é A / Então repositório retorna só A` (integração Testcontainers).
- `Dado ausência de comercio_id / Quando acessa recurso tenant-scoped / Então erro`.
- Tentativa de `SET` de outro comércio não burla a policy.
- `Dado um evento do comércio A / Quando o consumer processa / Então o contexto é A e nada de B é lido ou gravado`.
- `Dado o job com comércios A e B / Quando roda / Então processa cada um em transação e contexto próprios`.
- `Dado o job com A e B / Quando roda / Então logs de A só com comercioId=A, e o MDC limpo ao final`.
- `Dado a role do job / Quando lê tabela de domínio sem contexto / Então zero linhas`.

**NÃO FAZER.** Filtro de aplicação como substituto de RLS; schema-per-tenant agora (documentar como opção); rodar a app como superuser do Postgres; role de job com `BYPASSRLS`; processar vários comércios numa transação.

**Labels:** tenancy, rls, postgres, security

---

### REC-012 · [STORY] · 🔴 CRITICAL · 3 SP · EP-02
**IAM — autenticação OIDC/JWT e RBAC mínimo**

*Auditoria: E5 (papéis), §5.4 (IdP). Antes: 5 SP, 4 papéis. Agora: 3 SP, 2 papéis.*

**Descrição.** Autenticação e autorização do dashboard/API, delegadas a um IdP.

**Instruções de desenvolvimento.**
- Spring Security como **OAuth2 Resource Server** (`spring-boot-starter-security-oauth2-resource-server`, ADR-0002) validando JWT. JWK URI por env.
- ⚠️ **DECISÃO PENDENTE DO USUÁRIO — IdP (CONTEXTO §5.4):** Keycloak (self-host, mais operação) × Cognito (gerenciado, mais simples de operar, menos flexível). O card é implementado contra o padrão OIDC e não depende da escolha, salvo o ambiente de teste (use um JWT assinado de teste/WireMock do JWKS). A escolha precisa sair antes do deploy do piloto (REC-074).
- **Papéis no MVP: `SUPER_ADMIN` (operador da plataforma) e `OWNER`.** `MANAGER` e `SELLER` ficam na Fase 2. Claims: `sub`, `comercio_id`, `roles`.
- `SecurityFilterChain`: `/api/v1/**` autenticado; a rota de **ingestão driver→core** (REC-043) também é autenticada, com **HMAC por driver** (assinatura da requisição, segredo por driver, **independente do IdP**, decisão de 2026-11-08; segredo em variável de ambiente no piloto e no gestor de segredos no REC-074, mascarado em log, REC-005) e a checagem driver × `comercio_id` (detalhe no REC-043); essa rota não usa JWT; `/actuator/health`, `/webhooks/**`, `/swagger-ui/**` conforme política; `@PreAuthorize` por papel. CSRF off (stateless).

**Critérios de aceite.**
- Rota restrita a SUPER_ADMIN → 403 para OWNER.
- Token inválido/expirado → 401.
- `/webhooks/**` não exige JWT (usa HMAC próprio); a ingestão driver→core **não** é pública (REC-043).

**Cenários de teste.**
- `Dado usuário OWNER / Quando acessa rota SUPER_ADMIN / Então 403`.
- `Dado JWT expirado / Quando acessa rota protegida / Então 401`.
- `Dado rota pública / Quando acessa sem token / Então 200`.

**NÃO FAZER.** Auth própria com senha/hash (delegar ao IdP); proteger webhooks por JWT; segredos do IdP no repo; criar `MANAGER`/`SELLER` antes de existir usuário que precise.

**Labels:** iam, security, oidc, jwt

---

### REC-014 · 🆕 [SPIKE] · 🔴 CRITICAL · 1 SP · EP-02
**Decisão de criptografia de PII em repouso (ADR-0004)**

*Auditoria: I5. Precisa estar **Aceita antes do REC-020**, porque muda o schema de `cliente`.*

**Descrição.** Fechar a ADR-0004. **✅ DECIDIDO em 2026-11-08: Opção B** (ADR **Aceita**). Este card registra a decisão e a reflete nos cards seguintes.

**Instruções de desenvolvimento.**
- ✅ **DECIDIDO (2026-11-08): Opção B — criptografia por coluna na aplicação (AES-GCM).** Colunas cifradas: **telefone, CPF, e-mail e NOME**. **Índice cego (HMAC) no telefone** para busca e deduplicação. **Chave POR COMÉRCIO** sob uma **chave mestra**: no piloto a mestra fica em **variável de ambiente**; no deploy definitivo, **KMS/Secrets Manager (REC-074)**. A chave por comércio **permite** excluir os dados de um comércio descartando a chave; **o modo de exclusão (comércio e cliente) e a retenção ficam para o REC-072** (o usuário decidiu não fixá-los agora). O esquema técnico (chave aleatória/DEK em envelope, subchaves, nonce, AAD, versão) está na ADR-0004 e é implementado no REC-016.
- ADR-0004 com Status "Aceita" (feito), Consequências e itens de ação preenchidos; decisão refletida em REC-016 (componente), REC-020 (colunas `*_cifrado` + `telefone_hash`), REC-024 (dedup pelo índice cego), REC-035/REC-054 (chave do consentimento), REC-021/REC-055 (segredos, como consequência derivada) e REC-072.
- **Seguem pendentes (fora deste card):** termo/DPA com o comércio (frente F4) e política de retenção e modo de exclusão (REC-072, S9).
- Registrar o papel LGPD (comércio = controlador, plataforma = operadora) e o estado do termo/DPA (frente F4).

**Critérios de aceite.** ADR-0004 "Aceita" (**atendido em 2026-11-08**); REC-020/REC-024 citam a opção escolhida.

**Cenários de teste.** N/A (decisão documental).

**NÃO FAZER.** Implementar criptografia aqui; começar o REC-020 antes da decisão; decidir retenção/exclusão agora (fica no REC-072, S9).

**Labels:** adr, lgpd, pii, decisão

---

### REC-015 · 🆕 [SPIKE] · 🟠 HIGH · 1 SP · EP-07
**Decisão de montagem: onde roda o adapter de WhatsApp e como o core envia**

**Estado: concluído — decisão de 2026-11-08.** O card fica como registro; seu 1 SP **não entra na conta do S1** (S1 = 28 SP).

*Auditoria: I4b e I4c. Resolve a contradição entre o REC-050 ("core compila sem adapter") e o `wa-cloud` como lib; e entre o comando `mensagem.enviar` no contrato e o `MessagingPort` in-process.*

**Descrição.** Escolher e registrar (ADR curta ou seção na `spec.md` do REC-050) duas coisas: (b) em qual aplicação o `wa-cloud` roda e em qual mora o `/webhooks/whatsapp`; (c) se o envio é um **comando no contrato** ou uma **chamada in-process** à porta.

**Opções.**
- **Opção A (ESCOLHIDA em 2026-11-08):** `MessagingPort` é uma interface **in-process** do core. O `wa-cloud` é uma lib ligada ao `core-api` só em **runtime** (`runtimeOnly`; fora do classpath de compilação do core, com ArchUnit garantindo que o core não importa o adapter), descoberta por configuração Spring. O `/webhooks/whatsapp` mora no próprio `core-api`, no pacote do adapter, e entrega o status ao core por uma porta definida no core. O comando `mensagem.enviar` **não** entra no contrato.
- **Opção B:** `wa-cloud` é um serviço separado (como um driver) que consome o comando `mensagem.enviar` do contrato e publica `mensagem.status`. Mais isolado, mais peças para operar; exige transporte (REC-043) também para o envio.
- **Opção C:** módulo de montagem (um app fino que depende de `core-api` como lib e de `wa-cloud`). Mantém o core puro, mas cria um novo módulo e muda a estrutura do REC-001.

✅ **DECIDIDO em 2026-11-08: Opção A.** `MessagingPort` in-process; `wa-cloud` entra no `core-api` só como `runtimeOnly` (com ArchUnit); o `/webhooks/whatsapp` fica no `core-api`; o comando `mensagem.enviar` fica **FORA do contrato**. As Opções B e C ficam como registro histórico.

**Critérios de aceite.** Decisão registrada (Opção A, 2026-11-08); REC-010, REC-050 e REC-053 alinhados a ela.

**Cenários de teste.** N/A (decisão documental). Consequência técnica verificável no REC-050: ArchUnit falha se o core importar o adapter.

**NÃO FAZER.** Implementar o adapter aqui; deixar o core depender do `wa-cloud` em tempo de compilação.

**Labels:** adr, arquitetura, messaging, decisão

---

### REC-016 · 🆕 [STORY] · 🔴 CRITICAL · 5 SP · EP-02
**Componente de criptografia de PII (PiiCipher, BlindIndex, envelope de chaves)**

*Estimativa de 5 SP **condicionada** a: (i) usar Tink, não cripto montada à mão; (ii) sem o job de rotação do HMAC (só desenho + versão no ciphertext). Revisão do techlead (2026-11-08), item 9: nenhum card era dono do componente que a ADR-0004 (Opção B) exige. O REC-020 e o REC-024 **consomem** este card; não o reimplementam.*

**Descrição.** Entregar, em **`common`** (não no `core-api`: o driver também o usa, REC-021), o componente que cifra/decifra colunas de PII e calcula o índice cego, conforme o esquema técnico da ADR-0004. Fica no S1 porque o schema do REC-020 (S2) depende dele.

**Instruções de desenvolvimento.**
- **Biblioteca: Google Tink** (AEAD + keyset em envelope), **não** AES-GCM + HKDF montados à mão. Adicionar o Tink ao `gradle/libs.versions.toml` **só neste card** (não antes).
- `PiiCipher`: AEAD (AES-GCM, via Tink) por coluna; nonce aleatório (96 bits) por cifra, sem reuso; AAD = `comercio_id` + coluna; **versão da chave no ciphertext**; exceção tipada na falha de decifra (tag inválida ou chave ausente), sem engolir.
- `BlindIndex`: HMAC do telefone E.164 com o keyset **MAC por comércio** (Tink), separado do keyset AEAD.
- Envelope: **DEK aleatória por comércio** (keyset do Tink), guardada cifrada pela **mestra**; a mestra vem de variável de ambiente (piloto), atrás de uma interface (`MasterKeyProvider`) trocável por KMS/Secrets Manager no REC-074. Descartar a DEK inutiliza os dados do comércio (se o REC-072 escolher esse modo de exclusão).
- **Tabela de DEKs do core:** migration **`V0102__chave_comercio.sql`** (depois do `V0101` do REC-011, na ordem do S1) com `comercio_id`, `dek_aead_cifrada`, `dek_mac_cifrada` (os dois keysets Tink do comércio, ambos sob a mestra), `versao` e `criado_em`; `comercio_id` + RLS. O driver (REC-021) só precisa do keyset AEAD. **Quem cria a DEK:** a criação do comércio (REC-011/REC-013) chama o componente, na mesma transação. Os jobs/consumers leem a DEK **no contexto do tenant** (RLS), nunca por consulta fora dele.
- Conversores/utilitários para uso pelas entidades (ex.: `AttributeConverter`), para o REC-020, REC-021 e REC-055 reutilizarem.
- **Fora do escopo:** o job de rotação do HMAC. Entra só o **desenho** na `spec.md` (recalcular `telefone_hash` em `cliente`, `consentimento` e trilha no mesmo job, com o `unique` ativo); a **versão no ciphertext** permanece para a rotação futura.
- Mascaramento de log (REC-005) cobrindo a mestra, as DEKs e os keysets AEAD/MAC.

**Critérios de aceite.**
- Duas cifras do mesmo valor produzem ciphertexts diferentes; decifrar devolve o valor.
- Mesmo telefone em formatos distintos (após o normalizador do REC-024) → mesmo `telefone_hash`; o mesmo telefone em 2 comércios → `telefone_hash` diferente.
- Decifrar com a chave de outro comércio ou com AAD diferente falha com a exceção tipada.
- Nenhuma chave aparece em log, `toString` ou resposta de API.

**Cenários de teste.**
- `Dado o mesmo valor cifrado 2x / Então os ciphertexts são diferentes`.
- `Dado ciphertext do comércio A / Quando decifrado com a chave do B / Então falha tipada`.
- `Dado o mesmo telefone em 2 comércios / Então telefone_hash diferente`.
- `Dado ciphertext adulterado / Então tag inválida e exceção tipada (sem valor no log)`.
- `Dado DEK descartada / Então os dados do comércio não são mais decifráveis`.
- Teste de appender: mestra, DEK e keysets AEAD/MAC não saem em log.

**NÃO FAZER.** Montar AES-GCM/HKDF à mão em vez do Tink; implementar o job de rotação do HMAC; adicionar o Tink ao catálogo antes deste card; chave derivada de forma determinística da mestra como "chave por comércio"; reuso de nonce; mesma chave para AEAD e MAC; HMAC global; implementar KMS agora (REC-074); decidir exclusão/retenção (REC-072); logar chave ou valor.

**Labels:** pii, criptografia, lgpd, segurança

---

---

## Sprint 2 · Config do Comércio, Transporte, Ingestão & driver-stub
*Fase: Core + Driver · Semanas 5–6 · 26 SP*

🎯 **Meta (marco):** uma venda gerada pelo **driver-stub** aparece consolidada no core, com cliente identificado e telefone em E.164, **sem o core saber de nenhum ERP**. Valida a arquitetura Ports & Adapters ponta a ponta sem depender do Bling.

> **Gate:** a frente F3 (validação dos dados do comerciante-candidato) deve estar feita e o REC-014 (ADR-0004, **Aceita em 2026-11-08**) antes de começar o REC-020. **O transporte (REC-043, HTTP de ingestão + outbox, decidido em 2026-11-08) é pré-requisito do S2**: o `driver-stub` e o `driver-bling` são processos separados do core e precisam de um canal para entregar eventos. Ordem no sprint (o REC-016 já vem do S1): REC-013 → REC-043 → REC-020 → REC-024 → REC-023.

### REC-013 · [STORY] · 🟠 HIGH · 5 SP · EP-02
**Onboarding de comércio + configuração única do comércio**

*Auditoria: I3 (fonte única da janela N), I2 (fuso, atraso, inativo, teto), E5. Antes: 3 SP. Agora: 5 SP (a config passa a ser a fonte única de vários parâmetros).*

**Descrição.** Criar um comércio, definindo seu ERP e vertical, e seu usuário `OWNER`; e manter a **configuração do comércio**, que é a **fonte única** dos parâmetros de negócio usados pelos cards seguintes.

**Instruções de desenvolvimento.**
- Migration `V0201__config_comercio.sql` (config do comércio, sobre a tabela `comercio` do `V0101`).
- `POST /api/v1/comercios` (SUPER_ADMIN): cria `comercio` com `erp` e `vertical`, status `PENDENTE`.
- `GET /api/v1/comercios/{id}`; `PATCH /comercios/{id}/config`. `POST/PATCH /users` (papel `OWNER`).
- Estados: `PENDENTE → CONECTADO → ATIVO`. Erros RFC 7807.
- **Config do comércio (fonte única, com default documentado em código e neste card; todos os defaults são configuráveis por comércio — decididos em 2026-11-08, exceto o fuso):**

| Parâmetro | Uso | Default |
|---|---|---|
| `janela_n_dias` | antecedência do gatilho de reposição (N) | **5** (exemplo do §1.4) — default decidido em 2026-11-08 |
| `fuso_horario` | define "hoje" para jobs e janelas | `America/Sao_Paulo` (**provisório**) — ⚠️ **a definir com o piloto (F5)** (o piloto pode ser em Manaus, `America/Manaus`, UTC−4) |
| `tolerancia_atraso_dias` | quanto depois de `proxima_prevista` ainda se gera ação; também o prazo em que uma ação PENDENTE vira `EXPIRADA` | igual a `janela_n_dias` — default decidido em 2026-11-08 |
| `corte_inativo_ciclos` | cliente sem compra há mais de X× o intervalo é inativo | **2** — default decidido em 2026-11-08 |
| `max_acoes_cliente_semana` | teto de frequência por cliente (REC-041) | **1** — default decidido em 2026-11-08 |
| `janela_atribuicao_dias` | X da atribuição de conversão (REC-063) | **7** — default decidido em 2026-11-08 |
| `teto_mensagens_mes` | orçamento de envio (REC-056) | **não definido = envio bloqueado** até configurar — default decidido em 2026-11-08 |
| `messaging_provider` | adapter do comércio (REC-050) | `oficial` (único valor no MVP) |

- O REC-041 e o REC-062 **leem** `janela_n_dias`; o REC-062 só estende (override por vertical).

**Critérios de aceite.**
- Comércio criado com config default e status `PENDENTE`.
- Papel não-admin não cria comércio.
- Existe um único lugar de onde todo card lê `N`, fuso, atraso, inativo, teto e X.

**Cenários de teste.**
- `Dado SUPER_ADMIN / Quando cria comércio erp=bling / Então PENDENTE + os defaults documentados da config`.
- `Dado OWNER / Quando tenta criar comércio / Então 403`.
- `Dado PATCH de config com janela_n_dias inválida (≤0) / Então 422 (RFC 7807)`.

**NÃO FAZER.** Iniciar sync aqui; hardcodar verticais; criar UI; duplicar `N` em outro lugar; criar papéis além de SUPER_ADMIN/OWNER.

**Labels:** tenant, onboarding, config

---

### REC-043 · [STORY] · 🔴 CRITICAL · 5 SP · EP-01
**Transporte de eventos — ✅ DECIDIDO em 2026-11-08: HTTP de ingestão idempotente + outbox (Opção A)**

*Auditoria: E3 (recomendação, não ordem). Antes: "RabbitMQ — exchanges, filas, DLQ" (5 SP, com endpoint admin de DLQ). Agora: **5 SP** (eram 3; +2 pela autenticação da ingestão e pela durabilidade no driver, revisão de 2026-11-08), **sem endpoint admin de DLQ**; decidido em 2026-11-08: HTTP + outbox.*

**Descrição.** Para o piloto (1 comércio), escolher como o driver entrega eventos ao core. **Está no S2, antes do REC-020/023/022**: o driver é outro processo e precisa de um canal para falar com o core. O outbox (REC-042, S5) trata dos eventos **internos** do core e **não é pré-requisito** deste card. **A decisão (tomada em 2026-11-08: Opção A) destrava o S2.**

**Decisão e alternativa.**
- **Opção A — HTTP + outbox (ESCOLHIDA em 2026-11-08; é o transporte do MVP):** o driver envia os eventos canônicos ao core por **HTTP de ingestão idempotente** (o OpenAPI de ingestão já existe na ADR-0001, `ingestao.openapi.yaml`); os eventos internos do core usam outbox + poller no Postgres (REC-042, S5). Sem broker para operar. **Durabilidade no driver (decisão do usuário, 2026-11-08):** o driver **persiste antes de responder 202** um **outbox que guarda SÓ A REFERÊNCIA** (`erp_venda_id` + tipo do evento; pode reaproveitar a tabela de dedup com status). Na **reentrega** (com backoff), o driver **busca o pedido de novo no Bling** e republica; a reentrega **respeita o rate limit do Bling**. **Não há PII em repouso no driver por causa do outbox.** A memória do driver nunca é a única cópia da intenção de entrega. Esse **outbox durável é peça reutilizável do `driver-sdk`** (entregue neste card), e não código do driver-bling. **Falhas:** `V0204__falhas_ingestao.sql` fica **no core** e guarda a rejeição definitiva (4xx); no **driver**, o evento com tentativas esgotadas fica marcado como falho na própria tabela de outbox do driver (com `traceId`, classe e motivo; sem payload, pois só há a referência).
- **Autenticação da ingestão:** o endpoint de ingestão **não é público**. **Decisão do usuário (2026-11-08):** a credencial do driver no core é um **segredo/HMAC POR DRIVER** (assinatura da requisição), **independente do IdP**. O segredo fica em **variável de ambiente no piloto** e no **gestor de segredos no REC-074**, é **mascarado em log** (REC-005) e nunca vai em resposta de API. A rota entra no `SecurityFilterChain` do REC-012 com um filtro de verificação de HMAC (como nos webhooks), sem JWT. O core valida que o driver está **autorizado para o `comercio_id` do evento** (o tenant não vem confiável do corpo). TLS obrigatório fora da rede interna.
- **Opção B — RabbitMQ (registro histórico / alternativa futura; NÃO é opção do MVP, não implementar):** Spring AMQP (`spring-boot-starter-amqp`; renomeado `-rabbitmq` no 4.2, ADR-0002); topic exchange; filas por evento + DLX/DLQ; retry com backoff. **Sem** `POST /admin/dlq/{fila}/reprocessar` no MVP (reprocessar pelo console do RabbitMQ).

**Instruções de desenvolvimento.** Implementar a Opção A atrás do `CanonicalPublisher` (REC-004) e da porta de entrada do REC-020, sem mudar o contrato. Kafka fica documentado como migração futura.

**Rastreabilidade das falhas (obrigatório, REC-005).** Cada registro de falha (linha da tabela de falhas de ingestão) grava o **`traceId`**, a **classe da exceção** e o **motivo** do erro. **Regra fechada do payload:** nem o outbox do driver nem a tabela de falhas guardam payload com PII: guarda-se **só a referência** (`erp_venda_id` + tipo de evento). Se um dia algum registro precisar do payload, só **cifrado com a DEK do comércio** (ADR-0004/REC-016). **Nunca em claro.** A retenção desse registro fica no REC-072. O log da falha segue a política de níveis do REC-005 (um único `ERROR` na borda, sem body). **Log obrigatório:** (a) rejeição de autenticação/autorização = `WARN` com `traceId`, `driverId` e `comercio_id`, **sem body e sem PII**; (b) tentativas esgotadas no driver = **um `ERROR`** com `traceId`, `erp_venda_id` e tipo do evento, **sem payload**; (c) métrica de **backlog pendente por comércio** (outbox do driver).

**Critérios de aceite.** *(Estimativa: 5 SP, com o outbox durável no `driver-sdk` e a assinatura HMAC por driver.)* Evento do driver chega ao core de forma idempotente; falha repetida não perde mensagem (tabela de falhas de ingestão + reentrega com backoff); toda falha registra `traceId`, classe e motivo; ingestão sem credencial válida é rejeitada; o evento sobrevive a reinício do driver.

**Cenários de teste.**
- `Dado evento do driver / Quando entregue / Então o core consolida uma vez`.
- `Dado o mesmo evento 2x / Então estado idêntico`.
- `Dado uma falha definitiva de ingestão / Então o registro tem traceId, classe do erro e motivo, e o payload não traz PII em claro`.
- `Dado core indisponível / Então o driver reenvia com backoff`.
- `Dado o driver reiniciado durante o backoff / Então a referência persistida é reentregue depois (o pedido é buscado de novo no Bling)`.
- `Dado SELECT na tabela do outbox do driver / Então não há PII (só erp_venda_id e tipo)`.
- `Dado reentrega de muitos eventos / Então o rate limit do Bling é respeitado`.
- `Dado requisição de ingestão com assinatura HMAC inválida / Então 401 e nada persiste`.
- `Dado ingestão sem credencial / Então 401 e nada persiste`.
- `Dado driver que posta para um comercio_id ao qual não está vinculado / Então 403, nada persiste e WARN com traceId, driverId e comercio_id (sem body/PII)`.
- `Dado tentativas esgotadas no driver / Então um ERROR com traceId, erp_venda_id e tipo (sem payload) e o backlog por comércio aparece na métrica`.

**NÃO FAZER.** Kafka ou RabbitMQ agora; guardar o payload (PII) no outbox do driver; depender do IdP para a credencial do driver; endpoint de ingestão sem autenticação; confiar no `comercio_id` do corpo sem checar o vínculo do driver; guardar o evento só em memória antes do 202; perder mensagem sem tabela de falhas; endpoint admin de reprocessamento no MVP; mudar o contrato por causa do transporte; gravar payload com PII em claro na tabela de falhas.

**Labels:** transporte, mensageria, decisão

---
### REC-020 · [STORY] · 🔴 CRITICAL · 8 SP · EP-05
**Consumo de eventos canônicos e consolidação idempotente**

*Revisão de decisões 2026-11-08: o componente de cifra é do REC-016 (S1); o REC-020 o consome e continua em 8 SP. Auditoria: C2 (venda sem cliente), I2 (cancelamento, fuso), I5 (PII), E2 (sem Redis), E3 (transporte). A normalização E.164 e a dedup de cliente foram para o REC-024 (divisão para respeitar a escala de SP).*

**Descrição.** O core recebe eventos canônicos (de qualquer driver) e materializa Cliente/Produto/Venda/Item na base consolidada, sem jamais saber a origem.

**Instruções de desenvolvimento.**
- Entidades canônicas no core: `cliente`, `produto`, `venda`, `item_venda` (migration `V0202__ingestao.sql`), com `comercio_id` + RLS e uniques `erp_venda_id`/`erp_cliente_id`/`erp_produto_id` por comércio. **Colunas de PII conforme a ADR-0004** (Opção B, Aceita em 2026-11-08): `nome_cifrado`, `telefone_cifrado`, `cpf_cifrado`, `email_cifrado` (AES-GCM, DEK por comércio, via `PiiCipher` do **REC-016**), **`telefone_hash`** (índice cego HMAC via `BlindIndex`; usado em busca e `unique (comercio_id, telefone_hash)`) e a **versão da chave** (`chave_versao` ou prefixo do ciphertext). Nenhuma PII em claro nas colunas nem na tabela de falhas de ingestão. **A idempotência é a constraint `unique` no Postgres; não há Redis.**
- Consumers de `venda.canonica.registrada`, `venda.canonica.cancelada`, `cliente.canonico.upsert`, `produto/estoque`. A porta de entrada é o **HTTP de ingestão idempotente** (REC-043, decidido em 2026-11-08, que vem **antes** neste sprint): driver e core são processos distintos, então não há adaptador in-process. Tenant a partir do `comercio_id` do evento (REC-011). Upsert idempotente.
- **Venda sem cliente identificado:** não entra no motor. A venda é **ignorada para fins de recorrência** e contada na métrica `vendas_sem_cliente_total` (por comércio). Decisão de persistir ou não a venda "anônima" fica na `spec.md`; o requisito é que ela **não gere perfil/ciclo** e seja **contada**.
- **Venda cancelada/devolvida** (`venda.canonica.cancelada`): marca a venda como cancelada, **exclui-a do histórico** de recorrência e dispara o recomputo do cliente (emite `venda.persistida`).
- Derivar `qualidade_cadastro` do cliente (mínimo/parcial/completo) **na ingestão**, a partir do valor em claro ainda em memória; ela é gravada como coluna e é a única fonte para higienização/filtros (REC-061), pois o nome cifrado não permite SQL. Emitir `venda.persistida` (evento **interno**).
- "Hoje"/datas de negócio no fuso do comércio (REC-013).

**Critérios de aceite.**
- Evento `venda.canonica.registrada` → venda+itens+cliente consolidados e coerentes.
- Reprocessar o mesmo evento não duplica nem altera estado.
- Venda sem cliente não gera perfil/ciclo e incrementa a métrica.
- Cancelamento tira a venda do histórico e dispara o recomputo.
- O core não referencia nenhum tipo específico de ERP.
- Nome, telefone, CPF e e-mail ficam cifrados no banco (consumindo o REC-016, sem reimplementá-lo).
- **Falha de decifra** (tag AES-GCM inválida ou chave ausente) → `ERROR` com `traceId`, `comercio_id`, coluna e versão da chave, **nunca** ciphertext nem valor; exceção tipada, sem engolir.

**Cenários de teste.**
- `Dado um evento de venda / Quando consumido / Então venda+itens+cliente persistem`.
- `Dado o mesmo evento 2x / Quando consumido / Então estado idêntico (idempotência)`.
- `Dado item com produto ainda não espelhado / Quando consumido / Então trata conforme política (cria stub ou rejeita)`.
- `Dado venda sem cliente / Quando consumida / Então não cria perfil/ciclo e vendas_sem_cliente_total aumenta`.
- `Dado venda.canonica.cancelada / Quando consumida / Então a venda sai do histórico e o recomputo é disparado`.
- `Dado evento sem comercio_id / Então rejeitado`.
- `Dado um cliente persistido / Quando faço SELECT direto na tabela / Então nome, telefone, CPF e e-mail não aparecem em claro`.
- `Dado o mesmo valor de PII em 2 clientes / Então os ciphertexts são diferentes`.
- `Dado decifrar com a chave de outro comércio / Então falha tipada e ERROR com traceId, comercio_id, coluna e versão (sem ciphertext/valor)`.

**NÃO FAZER.** Conhecer o ERP de origem; calcular recorrência aqui; escrever no ERP; usar Redis; logar PII.

**Labels:** ingestion, canonical, idempotência

---

### REC-024 · 🆕 [STORY] · 🟠 HIGH · 3 SP · EP-05
**Identidade do cliente: normalização E.164 e deduplicação**

*Revisão de decisões 2026-11-08: consome o `BlindIndex` do REC-016; continua em 3 SP. Auditoria: C2. Antes embutido no REC-020; separado para respeitar a escala de SP.*

**Descrição.** O motor depende de o cliente ser **o mesmo** entre vendas e de o WhatsApp ser válido. No varejo pequeno, o mesmo cliente aparece com telefones em formatos diferentes ou em cadastros duplicados no ERP.

**Instruções de desenvolvimento.**
- Normalizar `telefone` para **E.164** (ex.: libphonenumber, região padrão BR). Telefone inválido ou ausente marca o cliente como **sem telefone válido** (não elegível a mensagem; continua no histórico).
- **Default decidido em 2026-11-08 (limitação conhecida):** **chave de deduplicação do cliente:** `(comercio_id, telefone_hash)` — o **índice cego (HMAC) do telefone E.164** (ADR-0004, Opção B), nunca o telefone em claro. Dois `erp_cliente_id` com o mesmo telefone normalizado apontam para **um só cliente** do core; os ids do ERP ficam registrados como referências (detalhar o modelo na `spec.md`). O histórico de compras é unificado.
- **Limitação conhecida (documentada, aceita em 2026-11-08):** telefone compartilhado (ex.: uma família usando o mesmo número) **funde históricos e distorce os ciclos** do cliente. Não se resolve no MVP.
- Migration `V0203__cliente_identidade.sql`.
- Busca/unique por telefone pelo **índice cego `telefone_hash`** (ADR-0004, Opção B), calculado pelo `BlindIndex` do **REC-016** sobre o E.164 normalizado; só o hash e o valor cifrado são persistidos. **O normalizador E.164 deste card é o único do sistema**: o REC-035 e o REC-054 o reutilizam, junto com o mesmo `BlindIndex`.
- Métricas: clientes deduplicados, telefones inválidos (por comércio).

**Critérios de aceite.**
- `(11) 99999-9999`, `+55 11 99999-9999` e `5511999999999` resultam no mesmo E.164 e no mesmo cliente.
- Telefone inválido não gera cliente elegível a mensagem.
- Reprocessar não cria cliente duplicado.

**Cenários de teste.**
- `Dado 2 cadastros do ERP com o mesmo telefone em formatos distintos / Quando consumidos / Então 1 cliente com histórico unificado`.
- `Dado telefone inválido / Então cliente marcado sem telefone válido e inelegível para envio`.
- `Dado o mesmo evento 2x / Então 1 cliente`.
- `Dado telefones em formatos distintos do mesmo número / Então o mesmo telefone_hash`.
- `Dado o mesmo telefone em 2 comércios / Então telefone_hash diferente e clientes independentes`.

**NÃO FAZER.** Normalizar no driver (a normalização é do core, o driver só repassa o texto); mesclar clientes por nome; logar telefone em claro.

**Labels:** cliente, e164, deduplicação, qualidade-dados

---

### REC-023 · 🆕 [STORY] · 🟠 HIGH · 5 SP · EP-04
**driver-stub (driver de simulação de vendas)**

*Auditoria: C3 e E4. Antecipa o que era o REC-073 como **ferramenta de teste**: S2–S5 não ficam bloqueados pelo Bling.*

**Descrição.** Um driver que **simula um ERP**: gera vendas, clientes e produtos sintéticos (com cenários controlados) e os publica como eventos canônicos, usando só `driver-sdk` e `contracts`. Alimenta os testes do core (recorrência, ações, e2e) enquanto o driver-bling não está pronto, e serve de base para a prova formal do 2º driver (REC-073, Fase 2).

**Instruções de desenvolvimento.**
- Novo módulo `drivers:driver-stub` (lib/app de uso **dev/test**, nunca em produção), dependendo somente de `driver-sdk` e `contracts`.
- Gerador configurável por cenário: cliente com compras a cada ~40 dias; cliente com 2 compras; compras no mesmo dia; venda sem cliente; venda cancelada; telefones em formatos variados; produto não recorrente. Dados 100% sintéticos (sem PII real).
- Publica `venda.canonica.registrada`, `venda.canonica.cancelada`, `cliente.canonico.upsert`, `produto.canonico.upsert` pelo `CanonicalPublisher`.
- ArchUnit cobre o módulo como qualquer driver.

**Critérios de aceite.**
- O stub publica eventos canônicos e o core os consolida **sem nenhuma referência ao stub no `core-api`**.
- Os cenários listados são reproduzíveis por nome.

**Cenários de teste.**
- `Dado o driver-stub com o cenário "recorrente 40 dias" / Quando publica / Então o core consolida venda+cliente e o histórico tem as compras esperadas`.
- `Dado o cenário "venda sem cliente" / Então a métrica de vendas sem cliente aumenta`.
- Teste ArchUnit: `core-api` não importa `com.recorra.driver.stub`.

**NÃO FAZER.** Usar PII real; subir o stub em produção; alterar o core para acomodar o stub; colocar regra de negócio no stub.

**Labels:** driver, stub, testes, ferramenta

> **✅ Marco do Sprint 2:** com REC-020/023/024, uma venda do stub aparece no core consolidada, e o core não tem uma linha de código sobre ERP.

---
---

## Sprint 3 · driver-bling & Consentimento
*Fase: Driver + Core · Semanas 7–8 · 21 SP*

🎯 **Meta (marco crítico):** uma venda registrada no **Bling** aparece consolidada no core **sem o core saber que o Bling existe**; e o **modelo de consentimento** com a `ConsentPolicy` única existe antes de qualquer ação ser gerada.

> **Gate:** REC-022 não começa sem a resposta do suporte do Bling sobre webhook e rate limit (frente F1, REC-019). O driver-stub (REC-023) mantém os sprints seguintes andando se o Bling atrasar.

### REC-021 · [STORY] · 🔴 CRITICAL · 8 SP · EP-04
**driver-bling — conexão OAuth2 e tokens por comércio**

**Descrição.** O driver autentica cada comércio no Bling e mantém os tokens válidos, isolados e cifrados.

**Instruções de desenvolvimento.**
- **O `code` e o `state` do callback OAuth nunca aparecem em log** (mascaramento da query string, REC-005).
- `POST /driver/v1/bling/conectar/{comercioId}` → URL de autorização (state assinado). `GET /driver/v1/bling/callback` → troca code por tokens.
- Persistir access+refresh **cifrados por comércio** com o mesmo esquema da ADR-0004 (consequência derivada, não decisão do usuário: `PiiCipher` do REC-016, DEK do comércio em envelope sob a mestra; mestra em variável de ambiente no piloto e KMS/Secrets Manager no deploy definitivo, REC-074). **O driver tem o SEU envelope:** DEK por comércio guardada **no banco do driver**, cifrada pela mestra (migration própria do driver, com `comercio_id`, `dek_cifrada`, `versao`, `criado_em` e RLS/isolamento por comércio; a DEK é criada no primeiro `conectar/{comercioId}`; os jobs leem a DEK no contexto do tenant). Um **eventual descarte da chave de um comércio** (se o REC-072 escolher esse modo de exclusão) **precisa cobrir o core e o driver**. `BlingTokenService.getValidAccessToken(comercioId)` com refresh automático. `TokenRefreshJob` horário (`@Scheduled`).
- Usar `ResilientErpClient` do `driver-sdk`. Emitir `driver.conexao.status`.
- Testes com WireMock simulando o IdP do Bling.

**Critérios de aceite.**
- Fluxo OAuth conclui → tokens cifrados → status `CONECTADO`.
- Access token expirado → refresh transparente.

**Cenários de teste.**
- `Dado comércio PENDENTE / Quando conclui OAuth / Então tokens cifrados + CONECTADO`.
- `Dado access token expirado / Quando precisa chamar o Bling / Então usa refresh e obtém novo token`.
- Token nunca aparece em log (teste de appender; mascaramento do REC-005).
- `Dado GET /callback?code=abc&state=xyz / Quando o request é logado / Então code e state saem mascarados ou omitidos`.

**NÃO FAZER.** Logar tokens; guardar em texto puro; compartilhar token entre comércios; chamar endpoints de dados ainda.

**Labels:** driver-bling, oauth2, security

---

### REC-022 · [STORY] · 🔴 CRITICAL · 8 SP · EP-04
**driver-bling — captura (webhook + backfill + reconciliação) → canônico**

*Auditoria: E2 (sem Redis), E6 (sem Spring Batch), E3 (transporte), C2 (telefone repassado como veio).*

**Descrição.** Capturar vendas do Bling em tempo real e em lote, normalizar para canônico e publicar ao core. Usa a decisão/fixtures do REC-019.

**Instruções de desenvolvimento.**
- Webhook `POST /webhooks/bling` autenticado por **HMAC**; **dedup por `erp_venda_id` numa tabela do driver com `unique` no Postgres (sem Redis)**. Responde 202 **só depois de persistir a referência** (`erp_venda_id` + tipo) no outbox do driver (REC-043; peça do `driver-sdk`), e processa assíncrono. A reentrega busca o pedido de novo no Bling e respeita o rate limit. Nunca loga o body do webhook (REC-005).
- No evento: `BlingClient.obterPedido` → mapear (MapStruct) payload Bling → `VendaCanonica` → publicar `venda.canonica.registrada` via `CanonicalPublisher` (transporte conforme REC-043). Pedido cancelado → `venda.canonica.cancelada`. O telefone é repassado **como veio** (normalização é do core).
- Backfill histórico: **`@Scheduled` + cursor retomável persistido** (paginado, respeitando rate limit). Spring Batch só se o volume real do backfill exigir (decisão posterior, fora do MVP). Polling incremental (`BlingIncrementalSyncJob`, 5–15 min) como rede de segurança, por cursor `ultima_sincronizacao`.
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
- `Dado o mesmo webhook 2x / Então 1 evento (dedup por unique)`.
- `Dado pedido cancelado / Então publica venda.canonica.cancelada`.
- Rate limit do Bling (429) respeitado no backfill e na reentrega do outbox.
- `Dado SELECT na tabela do outbox do driver / Então não há PII (só erp_venda_id e tipo)`.

**NÃO FAZER.** Vazar DTO do Bling para o core; processar síncrono no request do webhook; estourar rate limit; escrever no Bling; Redis; Spring Batch; logar body de webhook.

**Labels:** driver-bling, webhook, backfill, mapstruct

---

### REC-035 · 🆕 [STORY] · 🔴 CRITICAL · 5 SP · EP-07
**Modelo de consentimento + `ConsentPolicy` única**

*Auditoria: C1. Hoje a regra de opt-in está duplicada (REC-041 e REC-053) e o consentimento só nascia no S5. Este card cria a origem e a política única; REC-041 e REC-053 passam a **usar a mesma** `ConsentPolicy`.*

**Descrição.** O ERP não guarda consentimento, e uma base importada do Bling começa com 0% de opt-in. Este card cria o modelo, a trilha e a política única que decide se se pode enviar.

**Instruções de desenvolvimento.**
- Migration `V0301__consentimento.sql`: `consentimento` (estado atual, **chave `(comercio_id, telefone_hash)`**, em que `telefone_hash` é o índice cego HMAC por comércio do telefone E.164 — ADR-0004 Opção B, `BlindIndex` do REC-016 —, mais `telefone_cifrado` para uso na aplicação; **sem** `telefone_e164` em claro; `cliente_id` **opcional** associado depois, `OPT_IN`/`OPT_OUT`, `origem`, `prova`, `atualizado_em`) e `consentimento_evento` (trilha **append-only**: `telefone_hash` + `telefone_cifrado` (não o telefone em claro), tipo, origem, prova, timestamp; **a `prova`, se contiver PII, é cifrada**). `comercio_id` + RLS. **Cliente sem linha de consentimento é `SEM_REGISTRO`.** O consentimento pode nascer **antes** de o cliente existir (opt-in pela landing, REC-054) e é associado ao `cliente_id` quando o cliente chegar do ERP. O telefone é normalizado pelo **mesmo normalizador E.164** (REC-024) e indexado pelo **mesmo `BlindIndex`** (REC-016).
- `origem` (valores a detalhar na spec): captação por link/QR na loja, captação no recibo, importação com prova de consentimento prévio, registro manual (OWNER/SUPER_ADMIN). A captação em si é o REC-054.
- `ConsentPolicy.podeEnviar(cliente, tipo, agora)` — **classe única** no core: decide **somente** `OPT_IN` vigente + telefone válido (REC-024). **Não conhece a janela de 24h nem template × sessão**: isso é regra do adapter de WhatsApp (REC-051). Cliente `SEM_REGISTRO` **não** pode receber.
- `POST/DELETE /api/v1/clientes/{id}/consentimento` (opt-in/opt-out com timestamp e origem). (Movido do REC-053.)
- **Opt-out é imediato e definitivo até novo opt-in** e emite o evento interno `consentimento.revogado`, que o REC-040 consome para **cancelar as ações PENDENTES** do cliente.
- ArchUnit/teste: nenhuma outra classe consulta o estado de consentimento para decidir envio, só a `ConsentPolicy`.

**Critérios de aceite.**
- Cliente sem registro → `podeEnviar = false`.
- Opt-in registra origem e timestamp; opt-out vale na hora e fica na trilha.
- `consentimento.revogado` é emitido no opt-out.

**Cenários de teste.**
- `Dado cliente SEM_REGISTRO / Quando podeEnviar / Então false`.
- `Dado opt-in com origem=LINK_QR / Então registrado com timestamp e trilha`.
- `Dado opt-out / Quando podeEnviar / Então false imediatamente e consentimento.revogado emitido`.
- `Dado opt-out seguido de novo opt-in / Então podeEnviar true e a trilha tem os 3 eventos`.
- `Dado opt-in de um telefone sem cliente / Quando o cliente chega do ERP com esse telefone / Então o consentimento é associado ao cliente_id e podeEnviar = true`.
- `Dado o mesmo telefone em 2 comércios / Então consentimentos independentes e telefone_hash diferente`.
- `Dado cliente de outro comércio / Então não acessa (RLS)`.
- Teste de arquitetura: só a `ConsentPolicy` decide envio.

**NÃO FAZER.** Duplicar a regra de opt-in em outra classe; apagar a trilha; tratar `SEM_REGISTRO` como consentido; implementar a captação (REC-054); enviar mensagem aqui.

**Labels:** consentimento, lgpd, opt-in, consent-policy

---
---

## Sprint 4 · Motor de Recorrência
*Fase: Core · Semanas 9–10 · 22 SP*

🎯 **Meta:** a partir do histórico canônico, calcular recorrência/ciclo por cliente×produto e o perfil consolidado. Lógica pura e testável; recomputo reativo a cada venda. Ainda sem ações/mensagens.

### REC-030 · [TASK] · 🟠 HIGH · 3 SP · EP-06
**Entidades derivadas (PerfilConsumoCliente, CicloRecompra)**

**Descrição.** Estruturas que guardam o resultado da inteligência (ver Core de Negócio §1.4).

**Instruções de desenvolvimento.**
- Migration `V0401__inteligencia.sql`: `perfil_consumo_cliente` (ticket_medio, freq_media_dias, ultima_compra, segmento, ltv_estimado) e `ciclo_recompra` (cliente_id, produto_id, intervalo_medio_dias, proxima_prevista, ultima_compra, n_ocorrencias, confianca). `comercio_id` + RLS.
- Entidades/repositórios JPA.

**Critérios de aceite.** Migration aplica; uniques por (cliente,produto) em ciclo; campos default coerentes.

**Cenários de teste.** `Dado o schema / Quando persisto um ciclo / Então unicidade por (cliente,produto) impede duplicata`.

**NÃO FAZER.** Preencher `segmento` com regra agora (default `NOVO`); calcular nada aqui.

**Labels:** inteligência, schema

---

### REC-031 · [STORY] · 🔴 CRITICAL · 8 SP · EP-06
**RecurrenceCalculator — cálculo de ciclo (função pura)**

*Auditoria: I2 (≥3 compras, mesmo dia, quantidade, mediana fixada no §1.4).*

**Descrição.** O coração da inteligência. Dado o histórico canônico ordenado de um cliente, calcula o ciclo por produto. Deve ser **puro** (sem I/O), para ser trivialmente testável.

**Instruções de desenvolvimento.**
- Serviço `RecurrenceCalculator` recebe lista de compras (data, produto) e devolve `CicloRecompra` por produto: `intervalo_medio_dias` (**mediana** dos gaps), `ultima_compra`, `proxima_prevista = ultima + intervalo`, `confianca` e `n_ocorrencias`.
- **Confiança:** <2 compras = sem ciclo; **2 compras = baixa, calcula o ciclo mas não habilita ação**; **≥3 compras (≥2 gaps) = utilizável** (único nível que gera ação, REC-041).
- **Compras no mesmo dia** (mesmo produto) contam como **uma única ocorrência**; não existe gap zero.
- **Quantidade comprada não é considerada** (limitação do MVP; documentar no Javadoc e na spec).
- Compras de vendas canceladas já vêm excluídas do histórico (REC-020).
- Persistência separada do cálculo (um `IntelligenceService` orquestra: lê histórico → chama calculator → grava).

**Critérios de aceite.**
- Compras ~40 dias → intervalo ≈ 40, proxima_prevista = ultima + 40.
- 1 compra → sem ciclo.
- 2 compras → ciclo de confiança baixa, **não utilizável para ação**.
- Duas compras no mesmo dia = uma ocorrência.
- Cálculo não faz I/O.

**Cenários de teste.**
- `Dado compras do produto X em 01/01, 10/02, 22/03 / Então intervalo≈40 e confiança utilizável`.
- `Dado 1 única compra / Então não gera ciclo`.
- `Dado 2 compras / Então confiança baixa e não utilizável`.
- `Dado duas compras no mesmo dia / Então contam como uma ocorrência (sem gap zero)`.
- `Dado um outlier (uma compra muito fora) / Então a mediana não é distorcida` (justifica mediana vs média).
- `Dado quantidade 2 numa compra / Então o intervalo não é alterado` (documenta a limitação).

**NÃO FAZER.** ML/previsão complexa no MVP; I/O dentro do cálculo; gerar ação aqui; usar a quantidade para esticar o ciclo.

**Labels:** inteligência, recorrência, core-negócio

---

### REC-032 · [STORY] · 🟠 HIGH · 5 SP · EP-06
**Consolidação diária + recomputo reativo**

*Auditoria: E6 (sem Spring Batch), I4a (tenant em job/consumer), I2 (fuso).*

**Descrição.** Manter perfis/ciclos atualizados: em lote (diário) e reativamente (a cada venda).

**Instruções de desenvolvimento.**
- `DailyConsolidationJob` (**`@Scheduled` + cursor**, sem Spring Batch): enumera os comércios (REC-011), e **por comércio** (no fuso dele) recalcula ciclos/perfis dos clientes que compraram no dia. Idempotente e retomável por cursor.
- Consumer de `venda.persistida`: recomputa **só** o cliente da venda; emite `perfil.recalculado{clienteId}` (evento interno).

**Critérios de aceite.**
- Nova venda → perfil/ciclo do cliente atualizados; `perfil.recalculado` emitido.
- Rodar o job 2x = mesmo resultado.
- Job interrompido retoma do cursor sem reprocessar tudo.

**Cenários de teste.**
- `Dado uma venda persistida / Quando o consumer roda / Então o perfil do cliente é atualizado e o evento emitido`.
- `Dado o job diário rodando 2x / Então resultado idêntico (idempotência)`.
- `Dado dois comércios em fusos diferentes / Quando o job roda / Então "hoje" respeita o fuso de cada um`.

**NÃO FAZER.** Recalcular a base inteira todo dia (só afetados); recomputar outros clientes no reativo; gerar ação; usar Spring Batch.

**Labels:** inteligência, scheduler, eventos

---

### REC-034 · [TASK] · 🔵 MEDIUM · 3 SP · EP-06
**Endpoints de consulta (histórico, perfil, ciclos)**

**Instruções de desenvolvimento.** `GET /api/v1/clientes/{id}/historico|perfil|ciclos`, DTOs (não expor entidade JPA), paginação padrão. PII conforme ADR-0004.

**Critérios de aceite.** Retornam dados do cliente escopados ao comércio; 404 para id inexistente.

**Cenários de teste.** `Dado cliente com histórico / Quando GET /historico / Então lista paginada`; `Dado cliente de outro comércio / Então não retorna (RLS)`.

**NÃO FAZER.** Expor entidade JPA; vazar cross-tenant; logar PII.

**Labels:** api, consulta

---

### REC-046 · 🆕 [TASK] · 🟠 HIGH · 3 SP · EP-06
**Elegibilidade de produto para recompra (flag por produto/categoria)**

*Auditoria: I2 (produto não recorrente, como acessório ou ferramenta, não pode gerar ação).*

**Descrição.** Nem todo produto é consumível recorrente. Sem filtro, um produto de compra única gera ação de reposição sem sentido.

**Instruções de desenvolvimento.**
- Migration `V0402__produto_elegibilidade.sql`: `produto.elegivel_recompra boolean NOT NULL DEFAULT true`. Elegibilidade por categoria entra via `RegraVertical` (REC-062), que **estende** este card (a flag do produto, quando definida explicitamente, prevalece).
- `PATCH /api/v1/produtos/{id}/elegibilidade` (OWNER). Listagem de produtos com a flag.
- O gerador (REC-041) só considera ciclos de produtos elegíveis. Mudar a flag para não elegível depois da geração é motivo de `CANCELADA` das ações PENDENTES do produto (REC-040).

**Critérios de aceite.**
- Produto marcado não elegível não gera ação.
- Default = elegível (nenhum produto fica sem ação por esquecimento).

**Cenários de teste.**
- `Dado produto não elegível com ciclo utilizável / Quando o gerador roda / Então nenhuma ação`.
- `Dado produto novo / Então elegível por default`.
- `Dado produto elegível com ação PENDENTE / Quando o OWNER muda a flag para não elegível / Então a ação vira CANCELADA (motivo: produto inelegível)`.
- `Dado OWNER de outro comércio / Quando altera a flag / Então 404 (RLS)`.

**NÃO FAZER.** Hardcodar categorias; inferir elegibilidade por ML; criar regra por vertical aqui (é o REC-062).

**Labels:** inteligência, produto, elegibilidade

---
---

## Sprint 5 · Ações, Outbox & Transporte
*Fase: Core · Semanas 11–12 · 21 SP*

🎯 **Meta:** formalizar o ciclo de vida da ação, gerar a ação de **reposição** respeitando `ConsentPolicy`, e fechar o outbox dos **eventos internos** do core (o transporte driver→core já foi decidido no S2, REC-043). Sem envio real de WhatsApp.

### REC-040 · [STORY] · 🟠 HIGH · 5 SP · EP-06
**AçãoRecomendada + máquina de estados**

*Auditoria: I2 (EXPIRADA/CANCELADA/FALHA, chave de janela), C1 (opt-out cancela PENDENTES).*

**Instruções de desenvolvimento.**
- Migration `V0501__acao.sql`: `acao_recomendada` (tipo, cliente_id, produto_id, `janela_ref`, disparar_em, status, motivo). `comercio_id` + RLS.
- **Estados:** `PENDENTE`, `ENVIADA`, `CONVERTIDA`, `DESCARTADA`, **`EXPIRADA`**, **`CANCELADA`**, **`FALHA`**. Transições válidas, impostas em serviço:
  - `PENDENTE → ENVIADA | EXPIRADA | CANCELADA | FALHA` (`EXPIRADA` também por prazo, ver abaixo)
  - `ENVIADA → CONVERTIDA | DESCARTADA | FALHA`
  - `CONVERTIDA`, `DESCARTADA`, `EXPIRADA`, `CANCELADA`, `FALHA` são finais.
- **Quando:** `EXPIRADA` = (a) o cliente recomprou o produto antes do envio (nova venda do mesmo cliente+produto com a ação PENDENTE) ou (b) **default decidido em 2026-11-08:** a ação `PENDENTE` passou de `proxima_prevista + tolerancia_atraso_dias` sem ser enviada (envio bloqueado por template pendente, teto atingido ou job parado). Um job (ou a própria varredura do gerador) expira essas ações; **nunca se envia reposição fora da janela**; `CANCELADA` = opt-out do cliente (consumer de `consentimento.revogado`, REC-035), venda cancelada que invalida o ciclo, ou produto marcado inelegível (REC-046); `FALHA` = envio rejeitado ou "não entregue por política" da Meta, **sem reenvio** (REC-053/056).
- **Chave de janela:** `janela_ref` = data da `ultima_compra` do ciclo vigente (estável até uma nova compra). Unicidade ativa por `(cliente, produto, tipo, janela_ref)` entre ações em estado não final.

**Critérios de aceite.** Transição inválida é rejeitada; unicidade impede ação ativa duplicada; opt-out cancela as ações PENDENTES do cliente; recompra antes do envio expira a ação.

**Cenários de teste.**
- `Dado ação PENDENTE / Quando marco ENVIADA → CONVERTIDA / Então ok`; `Dado ação CONVERTIDA / Quando tento voltar para PENDENTE / Então erro`.
- `Dado ação PENDENTE / Quando o cliente recompra o produto / Então EXPIRADA`.
- `Dado 2 ações PENDENTES do cliente / Quando consentimento.revogado / Então ambas CANCELADAS`.
- `Dado ação PENDENTE com venda cancelada que invalida o ciclo / Quando venda.canonica.cancelada é processada / Então CANCELADA (motivo: venda cancelada)`.
- `Dado ação PENDENTE de um produto / Quando o produto passa a inelegível / Então CANCELADA (motivo: produto inelegível)`.
- `Dado ação PENDENTE além de proxima_prevista + tolerância sem envio / Quando o job de expiração roda / Então EXPIRADA e nunca é enviada depois`.
- `Dado falha de envio / Então FALHA e nenhuma tentativa de reenvio`.
- `Dado uma ação ativa na mesma janela_ref / Quando tento criar outra / Então rejeitada`.

**NÃO FAZER.** Cross-sell/reativação/VIP agora (só tipo `REPOSICAO`); reenviar ação em `FALHA`; transições fora da tabela.

**Labels:** inteligência, ações, state-machine

---

### REC-041 · [STORY] · 🔴 CRITICAL · 8 SP · EP-06
**ActionGeneratorJob — reposição na janela**

*Auditoria: I2 (≥3 compras, atraso, inativo, elegibilidade, teto de frequência, fuso), C1 (ConsentPolicy única), I3 (N da config).*

**Descrição.** Regra central de negócio: gerar a ação de reposição no momento certo (ver §1.4), respeitando opt-in pela `ConsentPolicy`.

**Instruções de desenvolvimento.**
- Para cada `ciclo_recompra` **utilizável (≥3 compras)** de produto **elegível**, com `proxima_prevista − N ≤ hoje ≤ proxima_prevista + tolerancia_atraso_dias` (**"hoje" no fuso do comércio**), criar `acao_recomendada(REPOSICAO)` se não existir ativa para a chave de janela (REC-040). `N`, tolerância, corte de inativo e teto vêm da **config do comércio** (REC-013); a `RegraVertical` (REC-062) só estende.
- **Atraso:** cliente atrasado ou job parado por dias continua elegível até `proxima_prevista + tolerancia_atraso_dias`; depois disso não gera ação.
- **Inativo:** cliente sem compra há mais de `corte_inativo_ciclos × intervalo_medio_dias` **não gera ação** (reativação é Fase 2).
- **Teto de frequência (default decidido em 2026-11-08: LIMITAR; consolidar fica na Fase 2):** no máximo `max_acoes_cliente_semana` ações geradas por cliente por semana. Havendo vários produtos vencendo, **limita-se** pela ordem de vencimento (a mais próxima de `proxima_prevista` primeiro); as demais ficam para a próxima execução enquanto ainda estiverem na janela. *(Consolidar vários produtos numa só mensagem é Fase 2.)*
- Job diário (`@Scheduled`, por comércio, REC-011) + reação a `perfil.recalculado`. Dedup. **Opt-in checado na geração pela `ConsentPolicy` (REC-035)**, sem regra duplicada. Emitir `acao.gerada` (evento interno).

**Critérios de aceite.**
- Cliente com 3+ compras e proxima_prevista em 4 dias e N=5 → cria 1 ação REPOSICAO PENDENTE.
- Cliente com apenas 2 compras → nenhuma ação.
- Cliente sem opt-in → nenhuma ação.
- Produto inelegível → nenhuma ação.
- Cliente atrasado dentro da tolerância → ação criada; fora dela → não.
- Cliente inativo → nenhuma ação.
- Não cria ação duplicada na mesma janela; respeita o teto de frequência.

**Cenários de teste.**
- `Dado ≥3 compras, proxima_prevista em 4d, N=5 e opt-in / Então 1 ação PENDENTE`.
- `Dado 2 compras (1 gap) / Então nenhuma ação`.
- `Dado cliente sem opt-in / Então nenhuma ação`.
- `Dado já existe ação ativa na janela / Então não cria outra`.
- `Dado proxima_prevista há 3 dias e tolerância 5 / Então cria; Dado há 9 dias / Então não cria`.
- `Dado cliente inativo (acima do corte) / Então não gera`.
- `Dado 3 produtos vencendo na semana e teto 1 / Então 1 ação por semana`.
- `Dado job parado 3 dias / Quando volta / Então gera as ações ainda dentro da janela e da tolerância`.
- `Dado comércios em fusos diferentes / Então "hoje" respeita cada fuso`.
- `Dado produto não elegível / Então nenhuma ação`.

**NÃO FAZER.** Enviar mensagem (S6); múltiplas ações por janela; gerar para inativo além do corte; reimplementar a regra de opt-in fora da `ConsentPolicy`; usar `N` de outra fonte que não a config.

**Labels:** inteligência, reposição, core-negócio

---

### REC-042 · [STORY] · 🟠 HIGH · 8 SP · EP-01
**Outbox pattern + publisher**

*Auditoria: C4 (traceId no outbox), E3.*

**Descrição.** Entregar eventos sem *dual-write*. O outbox serve aos **eventos internos do core** (`venda.persistida`, `perfil.recalculado`, `acao.gerada`, `mensagem.status`...) e é necessário **independentemente do transporte driver→core**. A entrega driver→core **não depende dele** (REC-043, S2).

**Instruções de desenvolvimento.**
- Migration `V0502__outbox.sql` (com colunas `trace_id`/`traceparent`). `OutboxService.record(evento)` grava na **mesma transação** do domínio, com o `traceId` corrente. `OutboxPublisher` (poller no Postgres) publica e marca enviado, restaurando o `traceId` no consumo. Retrofit dos eventos internos já emitidos nos S2–S4 (ponte direta) para passarem pelo outbox.
- O destino do publisher é o consumidor interno do core (sem broker no MVP, REC-043).

**Critérios de aceite.**
- Evento gravado no outbox na transação do domínio; publisher entrega e marca enviado.
- Falha no destino → evento permanece pendente e é reenviado.
- O `traceId` do outbox aparece nos logs do consumidor.

**Cenários de teste.**
- `Dado transação que grava no outbox / Quando commita / Então publisher entrega uma vez (at-least-once + consumidor idempotente)`.
- `Dado o destino fora / Quando publisher tenta / Então evento fica pendente e reenvia depois`.
- `Dado um evento gravado dentro de uma requisição com traceparent / Quando entregue / Então o consumidor loga o mesmo traceId`.

**NÃO FAZER.** Publicar no destino dentro da transação de negócio; deletar outbox antes de confirmar publish; perder o traceId.

**Labels:** outbox, eventos, consistência

---

---

## Sprint 6 · Mensageria & WhatsApp
*Fase: Core + Adapter · Semanas 13–14 · 23 SP*

🎯 **Meta:** deixar pronta a **mensageria pelo WhatsApp oficial** (Meta Cloud API): porta, **onboarding WhatsApp do comércio** (credenciais, template, template reprovado), adapter com janela de 24h e status, e **teto/custo de envio por comércio**. O consumer que fecha o loop ação → mensagem (REC-053) vem no S7.

> **Gate:** a frente F2 (Business verification da Meta, WABA, número e template aprovado do piloto) deve estar andando desde o início; REC-055 e REC-051 só se completam contra a conta real quando ela existir (os testes usam WireMock). Ordem no sprint: REC-050 → REC-055 → REC-051 → REC-056.

### REC-050 · [STORY] · 🟠 HIGH · 5 SP · EP-07
**MessagingPort (porta)**

*Auditoria: I4b/I4c (REC-015, Opção A decidida em 2026-11-08), C5 (um adapter só). Antes: "porta + comando `mensagem.enviar`" com dois adapters.*

**Instruções de desenvolvimento.** Interface `MessagingPort.enviar(MensagemCanonica)` no core; adapter resolvido por `messaging_provider` da config do comércio (no MVP, só `oficial`). Core só emite o pedido de envio; não conhece WhatsApp. Montagem conforme o **REC-015 (Opção A, decidida em 2026-11-08)**: `wa-cloud` ligado ao `core-api` só em **`runtimeOnly`**, descoberto por configuração Spring; o `/webhooks/whatsapp` mora no `core-api`, no pacote do adapter, e entrega o status ao core por uma porta definida no core. O comando `mensagem.enviar` **não** entra no contrato.

**Critérios de aceite.** Core compila e testa sem depender de nenhum adapter concreto (`wa-cloud` só `runtimeOnly`; ArchUnit: o core não importa `com.recorra.messaging..`); adapter resolvido em runtime por config.

**Cenários de teste.** `Dado comércio com provider=oficial / Quando o core envia / Então o adapter oficial é invocado` (adapter mockado); `Dado provider desconhecido / Então erro claro`; ArchUnit.

**NÃO FAZER.** Core conhecer detalhe de WhatsApp; acoplar a um provider; criar adapter de terceiro (ADR-0003).

**Labels:** engajamento, porta, hexagonal

---

### REC-055 · 🆕 [STORY] · 🔴 CRITICAL · 5 SP · EP-07
**Onboarding WhatsApp do comércio (credenciais, template, template reprovado)**

*Auditoria: C3b. O REC-051 assumia template aprovado e credencial por comércio sem nenhum card que os crie.*

**Descrição.** Cada comércio precisa de WABA, número, credenciais e um template de marketing aprovado pela Meta. Este card cria o cadastro e o ciclo de vida disso no core, para o envio ter o que usar. A aprovação em si é processo externo (frente F2).

**Instruções de desenvolvimento.**
- Migration `V0601__whatsapp.sql`: `whatsapp_conta` (por comércio: `waba_id`, `phone_number_id`, credencial/token **cifrado** conforme a ADR-0004, como consequência derivada: `PiiCipher` do REC-016 com a DEK do comércio) e `whatsapp_template` (por comércio: nome, idioma, categoria, variáveis, `status` = `PENDENTE`/`APROVADO`/`REPROVADO`, motivo). `comercio_id` + RLS.
- `POST/PATCH /api/v1/comercios/{id}/whatsapp` (SUPER_ADMIN): registra credenciais e templates e atualiza o `status` (informado pelo operador, ou pelo webhook de status de template da Meta quando houver).
- ⚠️ **DECISÃO PENDENTE DO USUÁRIO — como o comércio se conecta à Meta:** (padrão provisório, o mais simples) **cadastro manual** pelo operador `SUPER_ADMIN` com as credenciais do WABA do comércio × **Embedded Signup** da Meta (o comércio autoriza dentro do produto; mais automação, mais integração e exige ser Tech Provider/BSP). Este card implementa só o cadastro manual; o Embedded Signup seria um card novo.
- **Template reprovado ou pendente:** o envio do comércio fica **bloqueado** e a ação **permanece `PENDENTE`, não vira `FALHA`** (**default decidido em 2026-11-08**: template pendente = `PENDENTE`), com alerta/métrica, até haver template `APROVADO`. **A ação não fica PENDENTE para sempre:** passada a tolerância de atraso ela vira `EXPIRADA` (REC-040) e **não é enviada atrasada** quando o bloqueio cair. Não há reenvio nem resubmissão automática. O `SUPER_ADMIN` vê o motivo da reprovação.
- Nenhuma credencial em log nem em resposta de API (só mascarada).

**Critérios de aceite.**
- Comércio com template `APROVADO` e credenciais → o envio usa o template e a credencial dele.
- Comércio sem template aprovado → nenhum envio e um alerta/métrica.
- Credencial nunca aparece em log nem em GET.

**Cenários de teste.**
- `Dado comércio com credenciais e template APROVADO / Quando envia / Então usa o template do comércio`.
- `Dado template REPROVADO / Quando envia / Então bloqueado, ação continua PENDENTE e a métrica de bloqueio aumenta`.
- `Dado template pendente e ação PENDENTE além de proxima_prevista + tolerância / Quando o template é aprovado / Então a ação já está EXPIRADA e não é enviada`.
- `Dado OWNER / Quando tenta registrar credenciais / Então 403`.
- `Dado GET da conta WhatsApp / Então token não é devolvido`.
- Token nunca em log (teste de appender).

**NÃO FAZER.** Automatizar a aprovação ou a resubmissão de template; Embedded Signup agora; guardar token em texto puro; compartilhar credencial entre comércios; marcar a ação como `FALHA` só por template pendente.

**Labels:** whatsapp, onboarding, meta, template

---
### REC-051 · [STORY] · 🔴 CRITICAL · 8 SP · EP-07
**Adapter wa-cloud (WhatsApp Cloud API oficial)**

*Auditoria: C3b (credencial e template por comércio vêm do REC-055), I6 (política da Meta).*

**Instruções de desenvolvimento.** `messaging/wa-cloud`: `WhatsappClient` (WebClient) chamando Graph API `POST /{phone_number_id}/messages`; suporte a template (fora da janela 24h) e sessão (dentro). Credenciais e template **por comércio**, lidos do REC-055 (cifradas). **O REC-055 vem antes deste card no sprint** (credencial e template por comércio são pré-requisito). Decide **template × sessão** pela janela de 24h (regra do adapter; a `ConsentPolicy` não participa). Registrar `message_id` e custo por mensagem (~R$0,37 de marketing; valor configurável, não hardcoded). Erro da Meta e **"não entregue por política"** (limite de marketing por usuário) retornam `FALHA` **sem reenvio**. WireMock nos testes.

**Critérios de aceite.** Fora da janela → usa template aprovado; registra message_id e custo; falha por política vira `FALHA`, sem retry.

**Cenários de teste.** `Dado ação fora da janela 24h / Quando envia / Então usa template e registra message_id`; `Dado erro da Meta / Então marca falha e não quebra o fluxo`; `Dado "não entregue por política" / Então FALHA e nenhum reenvio`; token da Meta nunca em log.

**NÃO FAZER.** Mensagem de sessão fora da janela; hardcodar template ou custo; reenviar em falha por política; chip/API não oficial.

**Labels:** whatsapp, cloud-api, adapter

---

### REC-056 · 🆕 [STORY] · 🟠 HIGH · 5 SP · EP-07
**Orçamento/teto de envio por comércio e custo por mensagem**

*Auditoria: I6. ~R$0,37 por mensagem de marketing, sem saber quem paga e sem teto. A Meta limita marketing por usuário, então a entrega não é garantida.*

**Descrição.** Evitar gasto descontrolado e dar visibilidade de custo por comércio.

**Instruções de desenvolvimento.**
- Migration `V0602__orcamento_envio.sql` (contador mensal por comércio).
- `teto_mensagens_mes` na config do comércio (REC-013). **Sem teto configurado, o envio fica bloqueado** (default decidido em 2026-11-08).
- Serviço `OrcamentoEnvio` (reserva/consulta do contador) **entregue neste card (S6)**; o consumer de envio (REC-053, S7) o consulta antes de enviar. No teto, a ação **permanece `PENDENTE`** (default decidido em 2026-11-08: não vira `FALHA`) com alerta/métrica; reabre no mês seguinte ou ao subir o teto — **mas, passada a tolerância de atraso, a ação vira `EXPIRADA` (REC-040) e não é enviada atrasada.**
- Contador mensal por comércio (fuso do comércio) e **métrica de custo por comércio** (`mensagens_enviadas`, `custo_estimado`), alimentada pelo custo registrado no REC-051.
- **"Não entregue por política" da Meta = `FALHA`, sem reenvio** (REC-040/051/053). A métrica separa entregue, falha e falha por política.
- ⚠️ **DECISÃO PENDENTE DO USUÁRIO (comercial):** quem paga a mensagem (a plataforma, o comércio, repasse). Este card só mede e limita; a política de cobrança não está no escopo.

**Critérios de aceite.**
- Atingido o teto, nenhuma mensagem é enviada e as ações seguem PENDENTES.
- O custo por comércio é consultável.
- Falha por política não gera reenvio.

**Cenários de teste.**
- `Dado teto 100 e 100 reservas no mês / Quando OrcamentoEnvio é consultado / Então nega e a ação (no REC-053) continua PENDENTE`.
- `Dado ação PENDENTE bloqueada pelo teto além de proxima_prevista + tolerância / Quando o teto sobe no mês seguinte / Então a ação já está EXPIRADA e não é enviada`.
- `Dado comércio sem teto configurado / Então nenhum envio`.
- `Dado mês novo / Então o contador zera`.
- `Dado "não entregue por política" / Então FALHA, contabilizada à parte, sem reenvio`.
- `Dado dois comércios / Então contadores e custos isolados (RLS)`.

**NÃO FAZER.** Reenviar falha por política; cobrar ou faturar aqui; teto global em vez de por comércio; descartar a ação ao atingir o teto.

**Labels:** custo, teto, whatsapp, métrica

---

### REC-052 · ✂️ CORTADO do MVP · antes 5 SP · EP-07
**~~Adapter wa-terceiro (API alternativa + número dedicado)~~**

*Auditoria: C5 → **ADR-0003**. Risco de produto e jurídico (viola os termos do WhatsApp, número pode ser banido, contradiz a invariante nº 6). −5 SP.*

Este card **não será implementado** no MVP nem entra na Fase 2 sem uma nova ADR. A remoção do módulo `messaging:wa-terceiro` (settings, diretório e README) é escopo do **REC-001**. Só a porta `MessagingPort` permanece.

**Labels:** cortado, whatsapp

---

---

## Sprint 7 · Envio, Opt-in & Conversão
*Fase: Core · Semanas 15–16 · 21 SP*

🎯 **Meta:** fechar o loop **ação → mensagem** (consumer de envio respeitando `ConsentPolicy` e teto), ter **base de opt-in** (sem opt-in nada é enviado), feedback de ações e a **atribuição automática de conversão** — a métrica de ROI que vende o produto.

### REC-053 · [STORY] · 🔴 CRITICAL · 5 SP · EP-07
**Consumer de `acao.gerada` → envio + status**

*Auditoria: C1 (usa a `ConsentPolicy`; opt-in/opt-out e a captação saíram deste card para REC-035 e REC-054), I6, I4b (webhook no `core-api`, REC-015 Opção A decidida em 2026-11-08).*

**Descrição.** Fecha o loop ação → mensagem respeitando LGPD (§1.6). Entra no S7 porque consome o teto (REC-056) e o onboarding (REC-055), entregues no S6.

**Instruções de desenvolvimento.**
- Migration `V0701__mensagem.sql`: tabela `mensagem`. **Depende do REC-056 (`OrcamentoEnvio`) e do REC-055 (template/credencial), ambos do S6.**
- Consumer de `acao.gerada`: monta mensagem (template+variáveis) → **`ConsentPolicy.podeEnviar`** (a mesma do REC-035) → **teto de envio (REC-056)** → envia via `MessagingPort` → persiste `mensagem` (ENVIADA) → ação `ENVIADA`. Idempotente; throttle por número.
- Se a `ConsentPolicy` negar (ex.: opt-out depois da geração), a ação vai para `CANCELADA`, sem envio.
- Webhook `POST /webhooks/whatsapp` **no `core-api`, no pacote do adapter** (REC-015, Opção A; verificação da Meta + assinatura) → atualiza `mensagem` (entregue/lida/falha) → emite `mensagem.status` (evento interno). Status de falha ou "não entregue por política" → ação `FALHA`, **sem reenvio**. Nunca loga o body do webhook.

**Critérios de aceite.**
- Cliente com opt-in → mensagem enviada/registrada; ação vira ENVIADA.
- Cliente com opt-out → nada enviado.
- Reprocessar `acao.gerada` não reenvia.

**Cenários de teste.**
- `Dado opt-in / Quando acao.gerada / Então envia e registra, ação ENVIADA`.
- `Dado opt-out / Quando acao.gerada / Então não envia e a ação é CANCELADA`.
- `Dado o mesmo evento 2x / Então não reenvia`.
- `Dado teto atingido / Quando acao.gerada / Então não envia e a ação continua PENDENTE (até expirar)`.
- `Dado callback "entregue" / Então status da mensagem atualizado`.
- `Dado callback "não entregue por política" / Então ação FALHA e nenhum reenvio`.
- `Dado callback sem assinatura válida / Então 401 e nada processado`.

**NÃO FAZER.** Enviar sem passar por `ConsentPolicy`; reenviar ação; disparar em massa sem throttle; aceitar callback sem verificação; criar bot/conversa (fora do MVP); logar body do webhook.

**Labels:** engajamento, lgpd, webhook

---

### REC-054 · 🆕 [STORY] · 🔴 CRITICAL · 8 SP · EP-07
**Captação de opt-in (landing, QR/link, importação com prova)**

*Auditoria: C1. Nenhum card dizia COMO o cliente dá opt-in; uma base importada do Bling começa com 0% de opt-in e o piloto não enviaria nada.*

**Descrição.** Criar os meios de o cliente do comércio dar consentimento e de o comércio importar consentimentos que já possui, sempre gravando origem e timestamp pelo modelo do REC-035.

**Instruções de desenvolvimento.**
- Migration `V0702__optin_landing.sql` (token/identificador do link por comércio; o consentimento usa o `V0301`).
- **Landing de opt-in:** página pública mínima (HTML servido pelo próprio `core-api`, sem React), acessada por **link/QR** único por comércio. Pede nome (opcional), WhatsApp e o **aceite explícito** do envio de mensagens; mostra o nome do comércio (controlador) e como cancelar. Registra no REC-035 com `origem` e timestamp. Número normalizado pelo **mesmo normalizador E.164 do REC-024** e indexado pelo **mesmo `BlindIndex`** (REC-016): a landing e a importação (CSV) não têm normalização nem hash próprios. Se o telefone ainda não corresponde a um cliente do ERP, o consentimento fica **vinculado ao telefone** e é associado ao cliente quando ele chegar do ERP (modelo na `spec.md`).
- **Importação com prova de consentimento prévio:** `POST /api/v1/consentimentos/importacao` (OWNER; CSV) com telefone, origem e **referência/data da prova** do consentimento já obtido. **Linha sem prova é rejeitada.** Relatório de aceitas/rejeitadas.
- **Opt-out:** a landing e uma resposta de opt-out (processada via `POST/DELETE` do REC-035) revogam; o efeito sobre as ações PENDENTES está no REC-040.
- **IP e telefone nos logs (obrigatório, REC-005):** o rate limit usa IP, que é dado pessoal. Os logs de rate limit e de rejeição **não gravam o IP completo** (truncado, ex.: /24, ou hash com sal) **nem o telefone** (mascarado). Nenhum body da landing em log.
- Proteção do endpoint público: link assinado/identificador opaco do comércio, rate limit por IP, sem listar clientes, sem revelar se um telefone já existe.
- ⚠️ **DECISÃO PENDENTE DO USUÁRIO — estratégia de captação na loja:** QR no balcão, link/QR no recibo ou pedido do vendedor no ato da compra (e qual aviso/cartaz). **Padrão provisório (mais simples): QR/link único na loja + importação com prova.** O card entrega a landing e a importação, que servem a qualquer das três estratégias; a decisão define só o material e o processo da loja.
- ⚠️ **DEPENDE DE F4:** papel LGPD (comércio = controlador) e termo/DPA com o comércio; o texto da landing precisa ser validado por quem responde juridicamente.

**Critérios de aceite.**
- Cliente que preenche a landing fica com `OPT_IN`, origem e timestamp registrados.
- Importação aceita linhas com prova e rejeita linhas sem prova.
- Opt-out efetivo imediatamente.

**Cenários de teste.**
- `Dado o link do comércio A / Quando o cliente confirma o aceite / Então OPT_IN com origem LINK_QR e timestamp`.
- `Dado CSV com 3 linhas, 1 sem prova / Quando importa / Então 2 aceitas e 1 rejeitada no relatório`.
- `Dado telefone sem cliente no ERP / Quando opt-in / Então consentimento guardado e associado quando o cliente chegar`.
- `Dado o endpoint público / Quando excede o rate limit / Então 429`.
- `Dado um 429 ou uma rejeição / Quando logados / Então o log não contém o IP completo nem o telefone em claro`.
- `Dado opt-in e depois opt-out / Então podeEnviar false e trilha completa`.
- Teste: a página não vaza dado de outros clientes nem confirma que um telefone existe.

**NÃO FAZER.** Pré-marcar o aceite; importar consentimento sem prova; tratar importação do Bling como opt-in; React; coletar mais dado que o necessário (minimização); logar telefone em claro; logar o IP completo; logar body da landing.

**Labels:** opt-in, consentimento, lgpd, landing, importação

---

### REC-044 · [TASK] · 🔵 MEDIUM · 3 SP · EP-06
**Endpoint da fila de ações + feedback**

*Auditoria: I1b (a conversão passa a ser automática, REC-063; o feedback manual vira complemento/correção).*

**Instruções de desenvolvimento.** `GET /api/v1/acoes` (filtros tipo/status/data), `GET /acoes/{id}`, `PATCH /acoes/{id}` (feedback manual `DESCARTADA`, ou `CONVERTIDA` como **correção** da atribuição automática, ambos auditados com `origem=MANUAL`). Os estados `EXPIRADA`/`CANCELADA`/`FALHA` não são alteráveis por feedback.

**Critérios de aceite.** Feedback transita o estado e audita; não lista ações de outro comércio; estados finais automáticos não mudam.

**Cenários de teste.** `Dado ação ENVIADA / Quando dono marca DESCARTADA / Então transita e audita`; transição inválida → erro; `Dado ação FALHA / Quando tenta marcar CONVERTIDA / Então erro`.

**NÃO FAZER.** Transições inválidas; ver ações de outro comércio; depender do dono para a conversão.

**Labels:** api, ações, feedback

---

### REC-063 · 🆕 [STORY] · 🟠 HIGH · 5 SP · EP-08
**Atribuição automática de conversão**

*Auditoria: I1b. Depender de o dono marcar à mão "convertida" não funciona na prática; a conversão automática é a métrica de ROI.*

**Descrição.** Quando o cliente compra de novo o produto da ação, depois de ela ser enviada, a ação é marcada `CONVERTIDA` sem intervenção do dono.

**Instruções de desenvolvimento.**
- Migration `V0703__conversao.sql` (venda atribuída, origem, valor).
- Consumer de `venda.persistida`: para cada item da venda, se existe ação `ENVIADA` do **mesmo cliente + mesmo produto** e a data da venda está **depois do envio** e em até `janela_atribuicao_dias` (X, config do comércio, REC-013) → ação `CONVERTIDA` (`origem=AUTOMATICA`), registrando a venda atribuída e o valor.
- **X (`janela_atribuicao_dias`): default 7, decidido em 2026-11-08** (configurável por comércio).
- Idempotente (reprocessar a venda não duplica); havendo mais de uma ação ENVIADA para o par, atribui à **mais recente** (default decidido em 2026-11-08). Venda **anterior** ao envio não atribui; venda **fora** da janela não atribui; venda cancelada não atribui (já excluída do histórico, REC-020). **Default decidido em 2026-11-08:** reverter conversão por cancelamento posterior fica **fora do MVP** (limitação documentada).
- Métricas: `conversoes`, `taxa_de_conversao` (CONVERTIDAS ÷ ENVIADAS), `receita_atribuida`, por comércio e período; consumidas pelo dashboard/resumo (REC-060/064).

**Critérios de aceite.**
- Nova venda do mesmo cliente+produto dentro de X dias após ENVIADA marca CONVERTIDA automaticamente.
- Fora da janela, anterior ao envio ou de outro produto: não marca.

**Cenários de teste.**
- `Dado ação ENVIADA em D e venda do mesmo cliente+produto em D+3 e X=7 / Então CONVERTIDA (AUTOMATICA)`.
- `Dado a venda em D+10 e X=7 / Então continua ENVIADA`.
- `Dado venda de outro produto / Então não converte`.
- `Dado venda anterior ao envio / Então não converte`.
- `Dado a mesma venda processada 2x / Então uma única conversão`.
- `Dado 2 ações ENVIADAS para o par / Então a mais recente é convertida`.
- `Dado uma conversão automática / Então receita_atribuida aumenta pelo valor da venda`.

**NÃO FAZER.** Exigir feedback manual do dono; atribuir venda de outro cliente ou produto; contar a mesma venda duas vezes; reverter conversões nesta versão.

**Labels:** conversão, atribuição, roi, métrica

---
---

## Sprint 8 · Visibilidade, Higienização & Config
*Fase: Core · Semanas 17–18 · 21 SP*

🎯 **Meta:** visibilidade para o dono (agregações + **resumo semanal ou UI mínima**), higienização progressiva da base e as regras configuráveis por vertical.

### REC-060 · [STORY] · 🟠 HIGH · 8 SP · EP-08
**Endpoints de dashboard (agregações)**

**Instruções de desenvolvimento.**
- `GET /api/v1/dashboard/resumo` (novos clientes/dia, ticket médio, vendas, **ações enviadas, conversões, taxa de conversão, receita atribuída, custo de mensagens, base com opt-in, vendas sem cliente**), `/recorrencia` (previsão de reposição), `/top-clientes` (valor e recorrência; **decifra só o nome da página retornada**, sem filtro nem ordenação por nome no banco, ADR-0004), `/oportunidades` (fila do dia), `/previsao-faturamento`, `/produtos` (mais vendidos × cliente × ciclo).
- Agregar no banco (queries/`views materializadas`); usar réplica de leitura quando existir.

**Critérios de aceite.** Métricas escopadas ao comércio; período parametrizável; sem varredura em memória por request; métricas de conversão/custo vêm dos REC-063/056.

**Cenários de teste.** `Dado dados consolidados / Quando GET /resumo?periodo / Então métricas corretas e escopadas`; `Dado dois comércios / Então nenhum vazamento (RLS)`.

**NÃO FAZER.** Calcular em memória varrendo tabela inteira; vazar cross-tenant.

**Labels:** dashboard, agregação, performance

---

### REC-064 · 🆕 [STORY] · 🟠 HIGH · 3 SP (opção B, padrão) ou 8 SP (opção A) · EP-08
**Visibilidade para o dono no piloto — ⚠️ DECISÃO PENDENTE DO USUÁRIO (UI React mínima × resumo semanal)**

*Auditoria: I1a. O plano diz "visibilidade para o dono", mas não havia frontend nem card de React; o S6 original era só backend.*

**Descrição.** Dar ao dono do comércio-piloto uma forma de **ver o que o produto fez e quanto rendeu**.

**Opções.**
- **Opção B — Resumo semanal (padrão provisório, a mais simples; 3 SP):** job semanal (`@Scheduled`, por comércio, no fuso dele) que monta o resumo a partir do REC-060/063/056 e entrega **por e-mail** ao `OWNER`: ações enviadas, conversões, taxa de conversão, receita atribuída, custo, falhas (inclusive por política), tamanho da base com opt-in, vendas sem cliente. Variante por WhatsApp possível, mas exige template aprovado dedicado (REC-055) e consome o teto (REC-056); fica fora do padrão.
- **Opção A — UI React mínima (8 SP):** app React separado, login pelo IdP (REC-012), com 3 telas: resumo, fila de ações (REC-044) e tamanho da base de opt-in. Consome a API do REC-060/044. **Soma +5 SP ao MVP** (226 SP).

**Instruções de desenvolvimento.** Implementar a opção escolhida; sem decisão, vale a B. Em ambas, nenhuma PII além do necessário (agregados; nada de lista de telefones no e-mail).

**Critérios de aceite.**
- B: o `OWNER` recebe o resumo da semana por e-mail, com as métricas acima, só do próprio comércio.
- A: o `OWNER` autenticado vê as 3 telas, só do próprio comércio.

**Cenários de teste.**
- B: `Dado dados da semana / Quando o job roda / Então o e-mail traz as métricas corretas e só do comércio`; `Dado o job 2x na mesma semana / Então um único envio`.
- A: `Dado OWNER autenticado / Quando abre o resumo / Então vê só o seu comércio`; `Dado token expirado / Então redireciona ao login`.

**NÃO FAZER.** Expor PII em lote no resumo; UI além das 3 telas; começar a UI sem a decisão do usuário; construir editor de regras na UI.

**Labels:** dashboard, resumo, ui, decisão

---

### REC-061 · [STORY] · 🔵 MEDIUM · 5 SP · EP-08
**Higienização de base (DataQualityJob + fila de incompletos)**

**Instruções de desenvolvimento.** `DataQualityJob` semanal (`@Scheduled`, por comércio) usa a coluna `qualidade_cadastro` **calculada na ingestão (REC-020)**, nunca SQL sobre o nome (cifrado, ADR-0004); marca/lista a partir dela; `GET /api/v1/clientes/incompletos`; emite evento de solicitação de enriquecimento (a coleta via WhatsApp é Fase 2 — só emitir o evento).

**Critérios de aceite.** Clientes só com primeiro nome marcados como mínimo e listados em `/incompletos`.

**Cenários de teste.** `Dado cliente só com nome / Quando o job roda / Então marcado mínimo e aparece em /incompletos`.

**NÃO FAZER.** Alterar/apagar dado automaticamente; implementar a coleta de carro/atributos agora.

**Labels:** higienização, qualidade-dados

---

### REC-062 · [STORY] · 🔵 MEDIUM · 5 SP · EP-08
**Regras por vertical (config) — estende a config do comércio**

*Auditoria: I3 (a fonte única de `N` é o REC-013; este card só estende), I2 (elegibilidade por categoria).*

**Instruções de desenvolvimento.** `RegraVertical` (por comércio/vertical) **estende** os parâmetros da config do comércio: **override** de `janela_n_dias` por vertical/categoria e **elegibilidade por categoria** (a flag explícita do produto, REC-046, prevalece). Precedência documentada: **produto > categoria/vertical > config do comércio > default**. O domínio lê config; não contém regra de vertical hardcoded. No futuro, a recomendação por atributo.

**Critérios de aceite.** Alterar `N` por vertical/categoria muda a geração de ações sem deploy; nenhum `if (vertical == ...)` no domínio; ausência de regra cai na config do comércio.

**Cenários de teste.** `Dado override N=7 para a categoria / Quando o gerador roda / Então usa 7`; `Dado nenhuma regra / Então usa a config do comércio (REC-013)`; `Dado categoria inelegível e produto com flag explícita elegível / Então o produto é elegível`.

**NÃO FAZER.** Hardcodar regra de um vertical; misturar config com domínio; criar uma segunda fonte de `N`.

**Labels:** config, vertical, regras

---
---

## Sprint 9 · Hardening, LGPD & Piloto
*Fase: Qualidade · Semanas 19–20 · 19 SP*

🎯 **Meta:** nível de produção (testes e2e, resiliência, LGPD), **deploy simples** e piloto no ar, em paralelo ao sistema atual do comerciante.

> **Gate:** termo/DPA assinado (F4), template aprovado e número ativo (F2), opt-in capturado (REC-054) e teto configurado (REC-056). Sem isso o piloto sobe, mas não envia.

### REC-070 · [STORY] · 🟠 HIGH · 8 SP · EP-09
**Suíte de integração ponta a ponta + verificação do gate de cobertura**

*Auditoria: D5 (o gate JaCoCo ≥80% já roda no `check` desde o S0; aqui só se verifica), E2 (sem Redis), E3.*

**Instruções de desenvolvimento.** Cenário e2e: webhook Bling (WireMock) → driver → canônico → core → recorrência → ação (com opt-in) → WhatsApp (WireMock) → status → venda posterior → conversão automática. Testcontainers (Postgres). **Sem Redis.** Variante e2e com o driver-stub. **Verificar** (não ativar) que o gate de cobertura do `check` está ativo no CI.

**Critérios de aceite.** Fluxo e2e verde no pipeline; o gate de cobertura falha o build abaixo de 80%.

**Cenários de teste.** O próprio e2e é o cenário: `Dado uma venda no Bling e cliente com opt-in / Então uma mensagem de reposição é enviada ao fim do fluxo, e uma compra posterior marca CONVERTIDA`; `Dado cliente sem opt-in / Então nada é enviado`.

**NÃO FAZER.** Mockar o próprio domínio; baixar cobertura para passar; reintroduzir Redis.

**Labels:** qa, e2e, testcontainers

---

### REC-071 · [STORY] · 🔵 MEDIUM · 3 SP · EP-09
**Resiliência e reprocessamento**

**Instruções de desenvolvimento.** Revisar retries/CB de ERP/WhatsApp; reprocessamento de falhas (tabela de falhas de ingestão/reentrega, REC-043); alarmes de lag de sync e de backlog de reentrega/outbox; testes de falha injetada.

**Critérios de aceite.** Falhas transitórias não derrubam o fluxo; itens com falha definitiva ficam recuperáveis e reprocessam.

**Cenários de teste.** `Dado ERP intermitente / Quando sincroniza / Então recupera sem duplicar`.

**NÃO FAZER.** Silenciar exceção sem log/métrica; reprocessar sem idempotência; reenviar mensagem em `FALHA` por política.

**Labels:** resiliência, observabilidade

---

### REC-072 · [STORY] · 🟠 HIGH · 3 SP · EP-09
**LGPD ponta a ponta (verificação, retenção e exclusão)**

*Auditoria: C4 (mascaramento já existe desde o REC-005; este card o **verifica**), I5 (criptografia decidida na ADR-0004 e implementada nos cards de schema; aqui só retenção/exclusão).*

**Instruções de desenvolvimento.** **Verificar** (teste ponta a ponta de appender) que PII e tokens não aparecem em claro em nenhum log do fluxo e2e; **verificar** a PII em repouso conforme a ADR-0004 (Opção B, Aceita em 2026-11-08: colunas `*_cifrado` + `telefone_hash`, chave por comércio); opt-out efetivo imediato e trilha de consentimento (REC-035); **política de retenção** e **modo de exclusão** (de comércio e de cliente) — **pendentes, decididos aqui no S9** (o usuário decidiu não fixá-los antes; a ADR-0004 só registra que a chave por comércio *permite* excluir os dados de um comércio descartando a chave, com a ressalva sobre backups); rotina/endpoint de **exclusão a pedido** conforme essa decisão; minimização (MVP só nome+WhatsApp+CPF opcional).

**Critérios de aceite.** Pedido de exclusão remove/anonimiza PII e cessa envios futuros; nenhuma PII em log no e2e.

**Cenários de teste.** `Dado pedido de exclusão / Quando processado / Então PII removida/anonimizada e nenhum envio futuro (ações PENDENTES CANCELADAS)`; `Dado o fluxo e2e / Então nenhum log contém PII ou token`.

**NÃO FAZER.** Logar PII; reter além da política; usar PII no add-on de insights (só agregado/anonimizado); introduzir o mascaramento aqui (já é do REC-005).

**Labels:** lgpd, privacidade, segurança

---

### REC-074 · [STORY] · 🟠 HIGH · 5 SP · EP-01
**Deploy simples do piloto (infra mínima, segredos, flags, runbook)**

*Auditoria: E1. Antes: 8 SP com EKS, ArgoCD, Terraform, Amazon MQ e ElastiCache para 1 comércio-piloto. Agora: 5 SP, deploy simples. Kubernetes vira Fase 2.*

**Instruções de desenvolvimento.**
- Deploy simples: **1 VM, ECS ou PaaS** + **Postgres gerenciado**, imagens do `core-api` e do `driver-bling`. **Sem broker** (REC-043: HTTP + outbox) e **sem Redis.** A **chave mestra de PII** (ADR-0004) sai da variável de ambiente do piloto para **KMS/Secrets Manager** (ou equivalente da plataforma), assim como o **segredo HMAC por driver** (REC-043); as **DEKs ficam fora dos backups do banco** (ADR-0004). Segredos em Secrets Manager ou equivalente da plataforma. Pipeline de deploy manual ou simples (CI → imagem → deploy).
- ⚠️ **DECISÃO PENDENTE DO USUÁRIO — plataforma:** VM, ECS ou PaaS. **Padrão provisório (mais simples):** 1 VM com Docker Compose + Postgres gerenciado. Decidir também o IdP (REC-012) antes do deploy.
- **Provedor de e-mail (item de infra):** o resumo semanal do REC-064 (opção B) exige um provedor de e-mail transacional (SES, SMTP ou equivalente da plataforma): remetente/domínio verificado (SPF/DKIM), credenciais em segredo, limite de envio e teste de entrega. Entra no deploy e no runbook (⚠️ a escolha do provedor é do usuário; sem a opção B esse item cai).
- Feature flags por comércio (coluna na config do comércio, REC-013); runbook (onboarding do comércio, configuração do provedor de e-mail, conexão do Bling, onboarding WhatsApp, rollback, monitoramento, backup/restore do Postgres).

**Critérios de aceite.** Versão em prod com health check e rollback; comércio-piloto onboardado **em paralelo** ao sistema atual do comerciante; runbook testado uma vez.

**Cenários de teste.** Smoke test pós-deploy; rollback testado em ambiente de staging ou equivalente.

**NÃO FAZER.** Kubernetes/EKS/ArgoCD/Terraform/Amazon MQ/ElastiCache agora (Fase 2); cortar o sistema atual do comerciante; expor segredos; liberar tudo sem flag.

**Labels:** devops, deploy, runbook

---
---

## Fase 2 (backlog priorizável) — após o piloto
*Após o MVP validar core + 1 driver (+ a prova formal do 2º).*

Cada item vira um épico próprio com o mesmo nível de detalhe quando priorizado:

- **REC-073 · ✂️ MOVIDO · [SPIKE/STORY] · 8 SP · EP-04 — Prova formal do 2º driver.** *Auditoria: E4. Saiu do S7 do MVP: o `driver-stub` (REC-023) já existe como ferramenta desde o S2, e a prova formal vem **depois do piloto**.* Usando só `driver-sdk` e `contracts`, escrever um segundo driver (um ERP real simples **ou** a evolução do `driver-stub`) que publica eventos canônicos; o core consolida e gera ações como com o Bling, **sem alterar o `core-api`** (diff de `core-api` no PR = vazio). *NÃO FAZER:* alterar o core para acomodar o 2º ERP (se precisar, a abstração falhou — corrigir no contrato/sdk). Labels: driver, arquitetura, prova-de-conceito.
- **REC-08x Cross-sell por margem** (EP-06) — ação `CROSS_SELL`; requer dado de margem confiável.
- **REC-08x Segmentação** profissional × consumidor final (EP-06).
- **REC-08x Enriquecimento de cadastro via WhatsApp** (EP-07) — fluxo conversacional.
- **REC-08x Reativação de inativos** (EP-06).
- **REC-08x Consolidar vários produtos numa só mensagem** (EP-06) — alternativa ao teto de frequência do REC-041.
- **REC-08x Recomendação por regra de vertical** (EP-08) — config/plugin.
- **REC-08x Papéis `MANAGER` e `SELLER`** (EP-02) — quando existirem usuários que precisem.
- **REC-08x Embedded Signup da Meta / BSP oficial** (EP-07) — onboarding do WhatsApp sem cadastro manual.
- **REC-08x Infra de escala** (EP-01) — Kubernetes/EKS, ArgoCD, Terraform, broker gerenciado e cache, só quando o volume justificar. **Spring Batch** só se um backfill pedir.
- **REC-08x Novos drivers** (Tiny, Omie, Linx…) — ~58 PF / ~1 sprint cada, sem tocar no core.
- **REC-08x App no marketplace do Bling** (EP-01) — distribuição/GTM.
- **REC-08x Dashboards avançados**, **multi-unidade/rede**, **white-label (B2B2B)**.

**NÃO FAZER sem repriorização:** novos drivers antes do MVP provar o core; recomendação com ML antes da recorrência simples se mostrar insuficiente; adapter de WhatsApp não oficial (ADR-0003).

---

## Apêndice — Resumo de dependências e SP

```
S0 (25) → S1 (28) → S2 (26) → S3 (21) → S4 (22) → S5 (21) → S6 (23) → S7 (21) → S8 (21) → S9 (19)   = 227 SP (MVP)
```

Se o REC-064 for a UI React (opção A): **232 SP**. O REC-043 conta 5 SP. *(Em 2026-11-08: +5 SP do REC-016 no S1, +2 SP do REC-043 no S2 e −1 SP do REC-015, concluído e fora da conta; S1 = 28 e S2 = 26, aceitos pelo usuário sem mover cards.)*

**Dependências entre cards (setas = "precisa de"):**

```
REC-001 (inclui remover wa-terceiro) → REC-002, REC-003 (convenção V<sprint><seq>), REC-004, REC-005
REC-002 → REC-010 → REC-020, REC-023
REC-003 → REC-011 (RLS, Testcontainers)
REC-005 → todo card com log/PII (REC-020 em diante)
REC-011, REC-012 → REC-013 → (config lida por) REC-041, REC-056, REC-062, REC-063
REC-014 (ADR-0004 Aceita, Opção B) → REC-016 (PiiCipher/BlindIndex) → REC-020, REC-024, REC-035, REC-054, REC-021, REC-055, REC-072
REC-015 (concluído; Opção A) → REC-010 (sem comando), REC-050, REC-053
REC-019 (+ F1) → REC-021, REC-022
REC-020 → REC-024, REC-023 (stub publica; core consome), REC-031
REC-035 (ConsentPolicy) → REC-041, REC-053, REC-054;  REC-035 → REC-040 (consentimento.revogado)
REC-031 + REC-030 + REC-046 → REC-041 → REC-053
REC-040 → REC-041, REC-053, REC-063
REC-013 → REC-043 (HTTP de ingestão + outbox, decidido; S2) → REC-020 (porta de entrada), REC-023, REC-022
REC-042 (outbox, S5, só eventos internos) → retrofit dos eventos de S2–S4; REC-041, REC-053 (acao.gerada)
REC-050 → REC-055 (+ F2) → REC-051 (S6, nesta ordem) → REC-053 (S7)
REC-056 (S6, OrcamentoEnvio) → REC-053 (S7, consulta o teto antes de enviar)
REC-053 (S7) → REC-063 (S7, ação ENVIADA) → REC-060, REC-064
REC-054 → piloto (REC-074) · F4 (DPA) → piloto
REC-070 ← todos · REC-074 ← REC-012 (IdP decidido), REC-070
REC-073 (Fase 2) ← piloto, REC-023
```

**Frentes externas (fora do board, abrir já):** F1 suporte do Bling · F2 onboarding da Meta · F3 validação dos dados do comerciante-candidato · F4 papel LGPD/termo-DPA · F5 beachhead/piloto (§2.2).

Regra de ouro do desenvolvimento assistido: **um card = um PR pequeno e verde**; a seção `NÃO FAZER` é vinculante; **o core nunca aprende o nome de um ERP**.
