# AGENTS.md — guia para agentes de IA no Recorra

Este arquivo diz **onde está a verdade e em que ordem lê-la**. Ele não repete as regras: aponta para os documentos que as definem. Se algo aqui divergir deles, valem os documentos (e corrija este arquivo).

## 1. Ordem de leitura (discovery)

Antes de agir em qualquer tarefa:

1. **`tecDoc/CONTEXTO-PROJETO.md`** — o que é o produto, decisões de arquitetura fixas (§3), decisões em aberto (§5) e mapa dos artefatos (§8).
2. **`tecDoc/roadmap-desenvolvimento-sdd.md`**
   - §1 Core de negócio: linguagem ubíqua (§1.3) e **invariantes** (§1.6);
   - §3 Convenções do board: workflow (§3.2), **Definition of Done (§3.3)**, branches e commits (§3.4);
   - **o card inteiro** (`### REC-XXX`): descrição, instruções, critérios de aceite, cenários de teste e **NÃO FAZER**.
3. **`docs/adr/`** — toda ADR citada pelo card ou que toque o assunto. Índice em §2 abaixo.
4. **`specs/REC-XXX/`** — `spec.md` e `plan.md` do card, se já existirem (veja `specs/REC-001/` como modelo).
5. **`tecDoc/revisoes/`** — registros datados de auditorias e decisões do usuário. São **histórico**: o que foi aceito já está refletido no roadmap e nas ADRs; não edite esses arquivos.

## 2. Precedência e divergências

- Quando dois documentos se contradizem, ou um documento contradiz o código: **vale a ADR mais recente**, depois o card do roadmap, depois o CONTEXTO.
- **Aponte a divergência** (arquivo:linha dos dois lados) em vez de escolher em silêncio.
- Decisão em aberto (CONTEXTO §5 ou marcada ⚠️ no roadmap) **não se fecha por conta própria**: pergunte ao usuário.

| ADR | Assunto |
|---|---|
| [0001](docs/adr/0001-formato-contrato-canonico.md) | Contrato canônico: JSON Schema como fonte única, Java gerado |
| [0002](docs/adr/0002-baseline-spring-boot-4.md) | Baseline Spring Boot 4.1 (não 3.3) |
| [0003](docs/adr/0003-whatsapp-somente-api-oficial-no-mvp.md) | WhatsApp só pela API oficial da Meta no MVP |
| [0004](docs/adr/0004-criptografia-pii-em-repouso.md) | Criptografia de PII em repouso (Tink, chave por comércio) e papéis LGPD |

Ao criar uma ADR nova, acrescente-a a esta tabela.

## 3. Fronteiras que não se negociam

Resumo; o detalhe está no CONTEXTO §3 e nas ADRs.

- **`core-api` nunca conhece um ERP** — nenhum import de `com.recorra.driver..`, nenhum `if` por ERP. A única ponte core↔driver é `com.recorra.contracts`.
- **`contracts` é gerado** do JSON Schema (ADR-0001); nada escrito à mão ali, nenhum campo específico de ERP.
- **Drivers só leem do ERP.**
- **Multi-tenant** por `comercio_id` + RLS; idempotência por `erp_venda_id`; outbox para eventos internos.
- **PII cifrada** pelo componente do REC-016 (ADR-0004) e **nunca em log**.
- WhatsApp atrás da `MessagingPort`; regra de vertical é configuração (`RegraVertical`), não código.
- Convenções de build em `build-logic/`; versões só em `gradle/libs.versions.toml`.

## 4. Fluxo de um card (SDD)

`specs/REC-XXX/spec.md` (contratos, modelo tocado, casos de teste) → `specs/REC-XXX/plan.md` (passos) → código → um PR verde para `develop`.
Pronto = **DoD do roadmap §3.3**. Não altere nada fora do escopo do card.

## 5. Comandos

O JDK 21 vem do SDKMAN, que só carrega em shell interativo. Em shell não interativo, prefixe:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/current PATH=$HOME/.sdkman/candidates/java/current/bin:$PATH
```

Sem isso o macOS responde "Unable to locate a Java Runtime"; não falta Java nenhum.

```bash
./gradlew build             # compila, Spotless, SpotBugs, testes, JaCoCo (>= 80%)
./gradlew spotlessApply     # corrige a formatação
./gradlew :core-api:bootRun                # core em :8080
./gradlew :drivers:driver-bling:bootRun    # driver em :8081
```

Sempre pelo wrapper (`./gradlew`), nunca por `gradle`/`mvn` global.

## 6. Git

O usuário controla o git: **peça antes** de commitar, criar branch, dar push ou fazer merge, e antes de instalar ferramentas na máquina. Nomes de branch e commit seguem o roadmap §3.4.
