# Contexto do Projeto — Recorra
### Documento-âncora para retomar o trabalho em uma nova sessão

*Atualizado: 2026-11-08 (auditoria aplicada) · Autor: Luiz Henrique Albuquerque Coelho (dev — Java/Spring Boot)*

> **Para que serve este arquivo:** resumir todo o contexto do projeto num só lugar, para iniciar uma conversa nova do zero sem perder o fio. Leia isto primeiro.

---

## 1. O que é o projeto (em uma frase)

**Recorra** (codinome provisório) é uma **plataforma horizontal de inteligência de recompra e relacionamento proativo** para varejo B2C: lê as vendas que o comércio já registra no seu ERP/PDV, descobre o padrão de recompra de cada cliente e age **antes** do produto acabar, enviando a mensagem certa no WhatsApp — com visibilidade para o dono.

O diferencial não é cadastro nem cashback (commodities), é a **recompra antecipada automática**: tirar a inteligência de vendas da cabeça do vendedor e transformá-la num ativo do comércio.

---

## 2. Como chegamos aqui (história curta)

1. Começou como um sistema para **uma loja de estética automotiva** (Zaplus Car, franquia em Manaus). Fiz proposta, apresentei, o dono aprovou e ofereceu virar estudo de caso para escalar à rede.
2. **A Zaplus fechou → o projeto específico foi cancelado.**
3. Decidimos **generalizar**: o núcleo nunca foi "sobre estética automotiva" — é inteligência de recompra, que serve a qualquer varejo B2C com compra recorrente e cliente identificável (pet shop, farmácia/suplemento, cosmético, agro, auto peças, etc.).
4. Rearquitetamos para um **produto horizontal driver-based** e reescrevemos toda a documentação técnica nesse novo formato.

**Fase atual: INÍCIO DO SPRINT 0.** O bootstrap do monorepo (REC-001) já existe no repositório: 7 módulos Gradle, `build-logic` com convention plugins, version catalog, `core-api` e `driver-bling` como apps Spring Boot com `/actuator/health`. A auditoria de 2026-11-08 constatou que o `./gradlew build` ainda não foi verificado e que não há domínio nem endpoint de negócio. Os documentos de referência estão em `tecDoc/` (este arquivo e o roadmap) e `docs/adr/`. A auditoria produto × plano técnico está em `tecDoc/revisoes/2026-11-08-auditoria-produto-x-plano-tecnico.md` e seus achados foram aplicados no roadmap (cards novos, cortes e re-sequenciamento).

---

## 3. Decisões de arquitetura (fixas)

- **Ports & Adapters (hexagonal).** `core-api` é o domínio, **agnóstico de ERP**. Cada ERP tem um **driver** (anti-corruption layer) que traduz o ERP para um **modelo canônico** e empurra eventos ao core. Regra de ouro: o core nunca conhece um ERP concreto — proibido `if (erp == "bling")` no core.
- **Contrato canônico** é a fonte da verdade entre core e drivers (modelo + eventos canônicos). Definido como **JSON Schema** (fonte única, draft-07) referenciado por AsyncAPI 3.0 e OpenAPI 3.1, com classes Java **geradas** pelo `jsonschema2pojo` — **decisão fechada na ADR-0001**.
- **Monorepo** Gradle multi-módulo: `contracts`, `common`, `core-api`, `drivers/driver-sdk`, `drivers/driver-bling`, `messaging/wa-cloud` (`messaging/wa-terceiro` foi removido no REC-001: fora do MVP, ADR-0003). Deploy independente por serviço, CI por path.
- **Read-only no ERP.** Drivers só leem; nunca escrevem venda/nota (elimina risco fiscal).
- **Multi-tenant** por comércio (`comercio_id` + Row-Level Security no Postgres). Hierarquia `conta → comércio(unidade)`.
- **WhatsApp é uma porta** (`MessagingPort`). No MVP existe **um único adapter: a API oficial da Meta (Cloud API, ~R$0,37/msg de marketing)**. O adapter de terceiro/não oficial foi cortado do MVP por risco de produto e jurídico (**ADR-0003**). A porta permanece para outro provedor oficial no futuro.
- **Vertical-agnóstico:** regras de cada vertical entram como **configuração** (`RegraVertical`), não código.
- **Assíncrono por eventos**, idempotência por `erp_venda_id`. Transporte driver→core: **HTTP de ingestão idempotente + outbox/poller no Postgres** para eventos internos (REC-043, decidido em 2026-11-08; sem broker no MVP, RabbitMQ/Kafka só como alternativa futura na escala).

**Stack:** Java 21, **Spring Boot 4.1** (ADR-0002; subir para 4.2 quando sair o GA, previsto para nov/2026), Gradle 9, PostgreSQL 16 (RLS), Flyway, WebClient + Resilience4j, `@Scheduled` + cursor para os jobs, Testcontainers/WireMock, ArchUnit, GitHub Actions, Micrometer/OTel. Sem broker no MVP (REC-043). Frontend do dashboard: ⚠️ pendente (React mínimo × resumo semanal, REC-064).

**Fora do MVP (Fase 2):** Redis (dedup passa a ser tabela com `unique` no Postgres), Spring Batch (só se um backfill pedir), Kubernetes/EKS/ArgoCD/Terraform/Amazon MQ/ElastiCache (o piloto roda em deploy simples: 1 VM, ECS ou PaaS, com Postgres gerenciado), papéis `MANAGER`/`SELLER` (o MVP tem `SUPER_ADMIN` + `OWNER`).

---

## 4. Core de negócio (resumo)

- **Conceitos:** Conta, Comércio (tenant), Cliente, Produto, Venda/Item, CicloRecompra, PerfilConsumoCliente, AçãoRecomendada, Mensagem, RegraVertical.
- **Recorrência:** por cliente×produto, `intervalo_medio_dias` = **mediana** dos gaps; `proxima_prevista = ultima_compra + intervalo`; **2 compras = ciclo de baixa confiança, que não gera ação; só ≥3 compras (≥2 gaps) geram ação**. Compras no mesmo dia contam como uma ocorrência. A quantidade comprada não entra no cálculo (limitação do MVP).
- **Ação de reposição:** gerada quando `proxima_prevista − N ≤ hoje ≤ proxima_prevista + tolerância de atraso` (N = janela configurável por comércio, fonte única na config do comércio; "hoje" no fuso do comércio), se houver opt-in (`ConsentPolicy`), o produto for elegível e não existir ação ativa igual. Ação PENDENTE que passa da tolerância de atraso sem envio expira (⚠️ regra proposta, confirmar); nunca se envia reposição fora da janela.
- **Fluxo:** ERP → driver (captura+normaliza) → evento canônico → core (consolida) → inteligência (recorrência/perfil) → ação → WhatsApp → status → feedback.
- **Invariantes:** read-only no ERP; core agnóstico; idempotência; multi-tenant; opt-in/LGPD; regras da Meta para WhatsApp; minimização de dados (MVP só nome+WhatsApp).

---

## 5. Decisões em aberto (a fechar antes/no início do dev)

**Fechadas**

1. ~~**Formato do contrato**~~ — **fechada pela ADR-0001** (Aceita): JSON Schema como fonte única, Java gerado por `jsonschema2pojo`.
3. ~~**Adapter de WhatsApp padrão**~~ — **fechada pela ADR-0003** (Aceita): **só a Meta Cloud API oficial no MVP**; o adapter de terceiro foi cortado (auditoria C5). O onboarding da Meta (Business verification, WABA, número, template aprovado por comércio) é caminho crítico do piloto e deve começar já (ver "Frentes externas" no roadmap).
11. ~~**Criptografia de PII em repouso**~~ — **fechada em 2026-11-08 (ADR-0004, Aceita):** Opção B, criptografia por coluna na aplicação (AES-GCM) em telefone, CPF, e-mail e nome; índice cego (HMAC) no telefone; chave por comércio sob uma mestra (variável de ambiente no piloto, KMS/Secrets Manager no REC-074), o que **permite** excluir os dados de um comércio descartando a chave. Seguem pendentes: DPA com o comércio (F4) e **modo de exclusão e retenção (REC-072, S9; o usuário decidiu não fixar agora)**. Componente: REC-016.
12. ~~**Montagem do envio de WhatsApp (REC-015)**~~ — **fechada em 2026-11-08:** `MessagingPort` in-process; `wa-cloud` só `runtimeOnly` no `core-api` (ArchUnit); webhook no `core-api`; `mensagem.enviar` fora do contrato.
13. ~~**Transporte driver→core (REC-043)**~~ — **fechada em 2026-11-08:** HTTP de ingestão idempotente + outbox; RabbitMQ fica só como alternativa futura/registro histórico.
14. ~~**Defaults de negócio**~~ — **fechados em 2026-11-08** (configuráveis por comércio): N=5; tolerância de atraso=N; inativo após 2 ciclos; 1 ação/cliente/semana (limitar; consolidar é Fase 2); janela de atribuição 7 dias; dedup pelo telefone via índice cego (telefone compartilhado = limitação conhecida); PENDENTE além de `proxima_prevista`+tolerância → EXPIRADA; sem teto configurado = envio bloqueado; template pendente = PENDENTE; desempate "mais recente" e sem reversão de conversão. **Exceção: o fuso segue pendente** (ver item 15 em "Em aberto").

**Em aberto (decisão do usuário)**

15. **Fuso horário do comércio** — provisório `America/Sao_Paulo`; **a definir com o piloto (F5)** (pode ser `America/Manaus`, UTC−4). Config `fuso_horario`, REC-013.

2. **Estratégia de captura do Bling:** o portal público da API é insuficiente → **contatar o suporte do Bling** para confirmar webhooks de venda, rate limits e escopos. É o que destrava o driver-bling. **Abrir esta frente agora, fora do board** (não esperar o Sprint 1).
4. **IdP:** Keycloak (self-host) vs Cognito. *(a fixar; REC-012)*
5. **Nome/marca do produto:** hoje "Recorra" é só codinome nos pacotes.
6. **Vertical, ERP de praia (beachhead) e comerciante-piloto** para o go-to-market. Depende da análise do export real do Bling (taxa de vendas com cliente e telefone, clientes com ≥3 compras do mesmo SKU).
7. **Estratégia de captação de opt-in** na loja (QR, link no recibo, importação com prova de consentimento) e o **papel LGPD/DPA** com o comércio (REC-054, ADR-0004).
9. **Visibilidade para o dono no piloto:** UI React mínima × resumo semanal (REC-064).

---

## 6. Modelos de monetização (SaaS horizontal)

1. **SaaS self-serve por comércio** — setup + assinatura mensal (tiers); baixo custo marginal.
2. **App no marketplace do ERP (Bling)** — distribuição embutida na base do ERP (resolve o GTM sem cliente-âncora); possível rev-share.
3. **White-label / B2B2B** *(recomendado p/ escala)* — licenciar o motor para redes/franquias ou para o próprio ERP; uma venda alcança milhares de comércios.

**Dado-chave de esforço (IFPUG):** core ≈ 297 PF; **cada novo driver/ERP ≈ 58 PF** — o caro é core + 1º driver; cada ERP novo depois é barato e previsível.

---

## 7. Roadmap do MVP (resumo)

10 sprints de 2 semanas (S0–S9), regime parcial (~20–24 SP/sprint), **~227 SP · ~5 meses** (S1 = 28 e S2 = 26, acima da faixa de ~24, aceitos sem mover cards). O plano original tinha 8 sprints e ~176 SP no cabeçalho (os cards somavam 190); a auditoria acrescentou cards que faltavam (consentimento, captação de opt-in, onboarding da Meta, driver-stub, logging/PII, atribuição, resumo/UI, teto de envio) e cortou excesso (wa-terceiro, EKS, Redis etc.). Detalhe e mapa de numeração no roadmap (§2).

- **S0** Fundação do monorepo (contracts/geração, driver-sdk, Postgres, CI, ArchUnit, **logging/PII**)
- **S1** Contrato canônico v0 + multi-tenant/RLS + IAM + spike Bling + ADRs de PII e de montagem + **componente de criptografia de PII (REC-016)**
- **S2** Config do comércio + **transporte driver→core (REC-043: HTTP de ingestão + outbox)** + ingestão canônica + **driver-stub** — *marco: venda do stub consolidada no core*
- **S3** driver-bling (OAuth + captura) + **modelo de consentimento/`ConsentPolicy`** — *marco: venda no Bling consolidada sem o core conhecer Bling*
- **S4** Motor de recorrência + elegibilidade de produto
- **S5** Ações + outbox (eventos internos)
- **S6** Mensageria: `MessagingPort` + **onboarding WhatsApp do comércio** + adapter oficial + **teto/custo de envio**
- **S7** Consumer de envio + status, **captação de opt-in**, feedback de ações, **atribuição automática de conversão**
- **S8** Dashboard + resumo/UI (decisão pendente) + higienização + regras por vertical
- **S9** Hardening + LGPD + deploy simples do piloto
- **Fase 2** Prova formal do 2º driver (REC-073, após o piloto), cross-sell, segmentação, novos drivers, marketplace, white-label

**Próximo passo imediato:** abrir as frentes externas (suporte do Bling, processo da Meta, análise do export do comerciante-candidato, DPA), fechar as decisões em aberto (§5) e seguir com o **Sprint 0**. O desenvolvimento será **assistido por IA em Spec-Driven Development** — cada card do roadmap vira `spec.md` → `plan.md` → código, respeitando a seção "NÃO FAZER" de cada card.

---

## 8. Artefatos

**No repositório (fonte da verdade):**
- `tecDoc/CONTEXTO-PROJETO.md` — este arquivo.
- `tecDoc/roadmap-desenvolvimento-sdd.md` — **o principal para o dev**: core de negócio + cards (instruções, critérios, cenários de teste, SP, NÃO FAZER).
- `tecDoc/revisoes/2026-11-08-auditoria-produto-x-plano-tecnico.md` — auditoria do tech lead, aplicada no roadmap.
- `docs/adr/` — ADR-0001 (contrato canônico, Aceita), ADR-0002 (Boot 4.1, Aceita), ADR-0003 (WhatsApp só oficial, Aceita), ADR-0004 (criptografia de PII, **Aceita**, Opção B).

**Fora do repositório (não estão em `tecDoc/`; existiam na fase anterior de documentação, em outro local):**
- `desenho-tecnico-plataforma.md` / `.docx`, `plano-desenvolvimento-plataforma.md` / `.docx` — arquitetura, endpoints, casos de uso, pontos de função, precificação.
- `roadmap-desenvolvimento-sdd.docx` e `roadmap-sprints-plataforma.md` / `.docx` — o `-sdd.md` do repositório os supera; o `roadmap-sprints-plataforma` é a versão anterior e está obsoleta.

> Se esses arquivos forem necessários, localize-os com o autor e traga-os para `tecDoc/`. Nada neste repositório depende deles.

**Histórico (específico da Zaplus — referência, não mais o produto, fora do repositório):**
- `conceito-produto-estetica-automotiva.md`, `desenho-tecnico-e-requisitos-mvp.md`, `plano-desenvolvimento-tecnico.md`, `roadmap-sprints-jira.md`, e os decks `proposta-produto-estetica.pptx` / `proposta-mvp-roadmap.pptx`.

---

## 9. Como retomar numa nova sessão (sugestão de primeira mensagem)

> "Estou retomando o projeto **Recorra** (plataforma horizontal de inteligência de recompra, arquitetura driver-based / Ports & Adapters, monorepo Java/Spring). Anexo o `CONTEXTO-PROJETO.md` e o `roadmap-desenvolvimento-sdd.md`. Estamos no Sprint 0. Quero começar por [fechar a decisão X / montar o Sprint 0 / gerar o contrato canônico v0 / etc.]."

Assim a nova janela parte com todo o contexto e sem a poluição do histórico.
