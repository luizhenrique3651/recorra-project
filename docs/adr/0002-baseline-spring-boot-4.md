# ADR-0002: Baseline Spring Boot 4.1 (em vez de 3.3)

**Status:** Aceita
**Data:** 2026-11-07
**Decisores:** Luiz Henrique (dev/arquiteto)
**Substitui:** "Spring Boot 3.3" na stack do `CONTEXTO-PROJETO.md` §3 e no REC-001

---

## Contexto

A documentação fixou **Spring Boot 3.3**. Na data de início do desenvolvimento (nov/2026):

- O suporte OSS do **3.3 acabou em jun/2025**, e o do **3.5** (última linha 3.x) em **jun/2026**. Só existe suporte pago para eles.
- Linhas com patches gratuitos: **4.0.x** (até dez/2026) e **4.1.x** (até meados de 2027).
- O **4.2** está em RC, com GA previsto para nov/2026.

Começar um projeto greenfield numa linha sem patches de segurança contraria a invariante de produção (LGPD, dados de clientes, tokens OAuth de ERP).

## Decisão

- **Spring Boot 4.1.x** (versão inicial `4.1.1`) sobre **Java 21** e **Gradle 9**.
- **Subir para 4.2 assim que sair o GA**: é barato agora, enquanto o código é só esqueleto.
- Versões centralizadas em `gradle/libs.versions.toml`. Trocar de versão = 1 linha.

## Opções consideradas

| Opção | Prós | Contras |
|---|---|---|
| **3.3 (doc original)** | Sem nada novo para aprender | **Sem patches OSS desde jun/2025**; migração 3→4 forçada no meio do MVP |
| 3.5 | API 3.x conhecida, muito material disponível | Sem patches OSS desde jun/2026; mesma migração forçada |
| 4.0 | GA mais antigo | Suporte OSS termina em dez/2026, ou seja, já no Sprint 1 |
| **4.1 ✅** | Suportado até 2027; caminho curto para 4.2 | Mudanças do Boot 4 (abaixo) |

## Consequências (o que muda nos cards)

| Tema | Boot 3.x (como os cards supõem) | Boot 4.x | Cards afetados |
|---|---|---|---|
| Starter web | `spring-boot-starter-web` | `spring-boot-starter-webmvc` (starters modulares; cada um tem um `-test` companheiro) | REC-001 ✔ |
| JSON | Jackson 2 (`com.fasterxml.jackson.databind`) | **Jackson 3** (`tools.jackson.databind`); as anotações continuam em `com.fasterxml.jackson.annotation` | REC-002, REC-010 |
| springdoc | 2.x | **3.x** | REC-001 ✔ |
| Teste HTTP | `TestRestTemplate` | `RestTestClient` (o `TestRestTemplate` foi descontinuado) | todos com teste de integração |
| Mocks | `@MockBean` | `@MockitoBean` | todos |
| Testes | JUnit 5, Testcontainers 1.x | JUnit 6, Testcontainers 2.x (artefatos renomeados) | REC-003 |
| Resiliência | só Resilience4j | O Spring Framework 7 traz `@Retryable`/`@ConcurrencyLimit` nativos; o Resilience4j continua necessário para circuit breaker e rate limiter. Confirmar a versão compatível com o Boot 4 | REC-004 |
| RabbitMQ | `spring-boot-starter-amqp` | Renomeado para `spring-boot-starter-rabbitmq` no 4.2 | REC-042 |
| OAuth2 RS | `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` | REC-012 |

**Fica mais fácil:** patches de segurança gratuitos durante todo o MVP; virtual threads, API versioning e null-safety (JSpecify) nativos.
**Fica mais difícil:** há menos exemplos prontos na internet do que para 3.x, e alguns artefatos de terceiros ainda podem estar migrando. Na dúvida, procurar a versão "para Boot 4" de cada lib.

## Itens de ação

1. [x] REC-001 montado com Boot 4.1.1, springdoc 3.1.0 e Gradle 9.2.1.
2. [ ] Bump para Boot 4.2.x quando sair o GA (só alterar `spring-boot` no version catalog).
3. [x] Atualizar a stack no `CONTEXTO-PROJETO.md` §3.

## Notas de alteração

- 2026-11-08 — o transporte do MVP passou a ser HTTP de ingestão + outbox (REC-043); a linha RabbitMQ da tabela acima fica como contexto da época (referência futura, não usada no MVP). A decisão desta ADR não muda.
