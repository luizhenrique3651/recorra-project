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

Etapas de revisão de cada card, nesta ordem:
1. caça a erros;
2. revisões de arquitetura, performance e manutenibilidade;
3. tech lead;
4. warnings;
5. QA.

**QA:** além do teste funcional, o QA **analisa os testes escritos pelo dev** e **adiciona testes unitários, de integração ou funcionais quando julgar relevante**, por exemplo para cobrir lacunas nos cenários do card, bordas, invariantes do §1.6 ou PII fora de log e de `toString`.
- Faz isso **sem pedir confirmação** ao usuário nem ao tech lead.
- Escreve só em `src/test` e nunca altera código de produção.
- Os testes acrescentados entram no commit do card e são listados na entrega do QA.

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

O usuário controla o git.

- **Agentes podem criar branch** para a task (a partir da `develop`, com o nome do roadmap §3.4).
- **Agentes nunca commitam, dão push, fazem merge nem abrem PR**, nem com autorização implícita. Commit e PR são do usuário.
- Ciclo de entrega: o agente conclui a task e deixa o trabalho na árvore, **avisa o usuário** e **sugere a mensagem de commit** (formato do roadmap §3.4, ex.: `feat(REC-002): ...`); os dois revisam juntos; o usuário commita e avisa. **Só então** começa a próxima task.
- Instalar ferramentas na máquina: **peça antes**.

### 6.1 Execução em lote (várias tasks sem revisão intermediária)

Quando o usuário pedir uma sequência (ex.: "execute do REC-002 ao REC-005"), cada task segue o fluxo completo (§4 + revisões), **na ordem**, e cada uma é construída sobre a anterior, sem commit:

1. Crie a branch da task (`switch -c` a partir da branch da task anterior; a primeira sai da `develop`).
2. Implemente e passe pelas revisões até a task ficar pronta.
3. **Tire o snapshot** da árvore, sem commit, guardado fora do repositório:
   ```bash
   S=~/.cache/egeon-recorra; mkdir -p $S
   GIT_INDEX_FILE=$S/idx git read-tree HEAD && GIT_INDEX_FILE=$S/idx git add -A \
     && GIT_INDEX_FILE=$S/idx git write-tree > $S/snap-rec-XXX && rm $S/idx
   ```
4. Siga para a próxima task com a árvore como está.

No fim, entregue ao usuário **um único bloco** com o commit de cada branch e a **ordem das PRs**:

```bash
cd ~/Documents/recorraProject
S=~/.cache/egeon-recorra
git switch -C feature/REC-002-contracts-codegen develop
git read-tree $(cat $S/snap-rec-002) && git commit -m "feat(REC-002): ..."
git switch -C feature/REC-003-ci-github-actions
git read-tree $(cat $S/snap-rec-003) && git commit -m "chore(REC-003): ..."
# ... uma dupla de linhas por task, na ordem
git status   # deve ficar limpo
```

```text
PRs, nesta ordem (empilhadas):
1. feature/REC-002-... -> develop
2. feature/REC-003-... -> feature/REC-002-...  (ou -> develop, depois do merge da 1)
```

Regras do lote:
- `git switch -C` em cima do HEAD atual não mexe na árvore. O `read-tree` troca só o índice, e com isso cada commit leva exatamente o estado da sua task.
- O snapshot inclui tudo que não está no `.gitignore`. Antes de tirá-lo, confira o `git status` para que lixo de build (ex.: `build-logic/.kotlin/`) não entre no commit.
- Os snapshots são objetos git soltos e o `git gc` os apaga depois de ~2 semanas. Por isso o usuário deve commitar o lote antes disso.
- Se uma task falhar ou depender de decisão do usuário, **pare o lote ali** e entregue o bloco só até a última task pronta.
