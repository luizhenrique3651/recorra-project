# Contexto do Projeto — Recorra
### Documento-âncora para retomar o trabalho em uma nova sessão

*Atualizado: novembro/2026 · Autor: Luiz Henrique Albuquerque Coelho (dev — Java/Spring Boot)*

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

**Fase atual: PRÉ-DESENVOLVIMENTO.** Documentação concluída; prestes a montar o monorepo e iniciar o Sprint 0. Nenhuma linha de código escrita ainda.

---

## 3. Decisões de arquitetura (fixas)

- **Ports & Adapters (hexagonal).** `core-api` é o domínio, **agnóstico de ERP**. Cada ERP tem um **driver** (anti-corruption layer) que traduz o ERP para um **modelo canônico** e empurra eventos ao core. Regra de ouro: o core nunca conhece um ERP concreto — proibido `if (erp == "bling")` no core.
- **Contrato canônico** é a fonte da verdade entre core e drivers (modelo + eventos canônicos). Definido como **schema neutro** (AsyncAPI/JSON Schema + OpenAPI) com classes Java **geradas** — *(decisão a confirmar: schema neutro vs jar Java simples)*.
- **Monorepo** Gradle multi-módulo: `contracts`, `common`, `core-api`, `drivers/driver-sdk`, `drivers/driver-bling`, `messaging/wa-cloud`, `messaging/wa-terceiro`. Deploy independente por serviço, CI por path.
- **Read-only no ERP.** Drivers só leem; nunca escrevem venda/nota (elimina risco fiscal).
- **Multi-tenant** por comércio (`comercio_id` + Row-Level Security no Postgres). Hierarquia `conta → comércio(unidade)`.
- **WhatsApp é uma porta** (`MessagingPort`) com adapters: oficial (Meta Cloud API, ~R$0,37/msg, número seguro) vs terceiro (número dedicado, mais barato, mais risco).
- **Vertical-agnóstico:** regras de cada vertical entram como **configuração** (`RegraVertical`), não código.
- **Assíncrono por eventos** (outbox + RabbitMQ → Kafka na escala), idempotência por `erp_venda_id`.

**Stack:** Java 21, Spring Boot 3.3, PostgreSQL 16 (RLS), Flyway, RabbitMQ, Redis, WebClient + Resilience4j, Spring Batch, Testcontainers/WireMock, ArchUnit, Docker/Kubernetes (EKS), GitHub Actions + ArgoCD, Grafana/OTel. Frontend do dashboard: React (separado).

---

## 4. Core de negócio (resumo)

- **Conceitos:** Conta, Comércio (tenant), Cliente, Produto, Venda/Item, CicloRecompra, PerfilConsumoCliente, AçãoRecomendada, Mensagem, RegraVertical.
- **Recorrência:** por cliente×produto, `intervalo_medio_dias` = mediana dos gaps; `proxima_prevista = ultima_compra + intervalo`; confiança cresce com nº de compras.
- **Ação de reposição:** gerada quando `hoje ≥ proxima_prevista − N` (N = janela configurável), se houver opt-in e não existir ação ativa igual.
- **Fluxo:** ERP → driver (captura+normaliza) → evento canônico → core (consolida) → inteligência (recorrência/perfil) → ação → WhatsApp → status → feedback.
- **Invariantes:** read-only no ERP; core agnóstico; idempotência; multi-tenant; opt-in/LGPD; regras da Meta para WhatsApp; minimização de dados (MVP só nome+WhatsApp).

---

## 5. Decisões em aberto (a fechar antes/no início do dev)

1. **Formato do contrato:** schema neutro (gera Java; permite drivers de terceiros/poliglotas) vs jar Java compartilhado (mais simples). *Recomendado: schema neutro.*
2. **Estratégia de captura do Bling:** o portal público da API é insuficiente → **contatar o suporte do Bling** para confirmar webhooks de venda, rate limits e escopos. (É o que destrava o driver-bling.)
3. **Adapter de WhatsApp padrão:** oficial (pago, seguro) vs terceiro (barato, risco).
4. **IdP:** Keycloak (self-host) vs Cognito. *(a fixar)*
5. **Nome/marca do produto:** hoje "Recorra" é só codinome nos pacotes.
6. **Vertical e ERP de praia (beachhead)** para o go-to-market.

---

## 6. Modelos de monetização (SaaS horizontal)

1. **SaaS self-serve por comércio** — setup + assinatura mensal (tiers); baixo custo marginal.
2. **App no marketplace do ERP (Bling)** — distribuição embutida na base do ERP (resolve o GTM sem cliente-âncora); possível rev-share.
3. **White-label / B2B2B** *(recomendado p/ escala)* — licenciar o motor para redes/franquias ou para o próprio ERP; uma venda alcança milhares de comércios.

**Dado-chave de esforço (IFPUG):** core ≈ 297 PF; **cada novo driver/ERP ≈ 58 PF** — o caro é core + 1º driver; cada ERP novo depois é barato e previsível.

---

## 7. Roadmap do MVP (resumo)

8 sprints de 2 semanas, regime parcial (~20–24 SP/sprint), **~176 SP · 3 a 5 meses**:

- **S0** Fundação do monorepo (contracts/geração, driver-sdk, Postgres, CI, ArchUnit)
- **S1** Contrato canônico v0 + multi-tenant/RLS + IAM + spike Bling
- **S2** Ingestão canônica + driver-bling (auth + captura) — *marco: venda no Bling consolidada sem o core conhecer Bling*
- **S3** Motor de recorrência
- **S4** Ações + outbox + broker (RabbitMQ)
- **S5** Mensageria + adapters WhatsApp + opt-in
- **S6** Dashboard + higienização + config de vertical
- **S7** Hardening + LGPD + piloto + **prova do 2º driver** (diff do core = vazio)
- **S8+** Fase 2 (cross-sell, segmentação, novos drivers, marketplace, white-label)

**Próximo passo imediato:** fechar as decisões em aberto (§5) e iniciar o **Sprint 0** (montar o monorepo). O desenvolvimento será **assistido por IA em Spec-Driven Development** — cada card do roadmap vira `spec.md` → `plan.md` → código, respeitando a seção "NÃO FAZER" de cada card.

---

## 8. Artefatos já produzidos (arquivos)

**Documentação técnica (versão genérica, atual):**
- `desenho-tecnico-plataforma.md` / `.docx` — arquitetura driver-based, contrato canônico, portas.
- `plano-desenvolvimento-plataforma.md` / `.docx` — stack, monorepo, endpoints, mensageria, casos de uso, pontos de função, precificação.
- `roadmap-desenvolvimento-sdd.md` / `.docx` — **o principal para iniciar o dev**: core de negócio + cards Jira detalhados (instruções de dev, critérios, cenários de teste, SP, NÃO FAZER).
- `roadmap-sprints-plataforma.md` / `.docx` — versão anterior (mais enxuta) do roadmap; a `-sdd` a supera.

**Histórico (específico da Zaplus — referência, não mais o produto):**
- `conceito-produto-estetica-automotiva.md`, `desenho-tecnico-e-requisitos-mvp.md`, `plano-desenvolvimento-tecnico.md`, `roadmap-sprints-jira.md`, e os decks `proposta-produto-estetica.pptx` / `proposta-mvp-roadmap.pptx`.

---

## 9. Como retomar numa nova sessão (sugestão de primeira mensagem)

> "Estou retomando o projeto **Recorra** (plataforma horizontal de inteligência de recompra, arquitetura driver-based / Ports & Adapters, monorepo Java/Spring). Anexo o `CONTEXTO-PROJETO.md` e o `roadmap-desenvolvimento-sdd.md`. Estamos em pré-desenvolvimento. Quero começar por [fechar a decisão X / montar o Sprint 0 / gerar o contrato canônico v0 / etc.]."

Assim a nova janela parte com todo o contexto e sem a poluição do histórico.
