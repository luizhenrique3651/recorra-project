# ADR-0005: Inspeções do IntelliJ (Qodana) adiadas até haver um servidor Docker dedicado

**Status:** Aceita
**Data:** 2026-11-10
**Decisores:** Luiz Henrique (dev/arquiteto)
**Afeta:** fluxo de revisão dos cards (caça a erros e warnings, `AGENTS.md` §4); REC-075

---

## Contexto

As revisões assistidas por IA rodavam o **Qodana Community** (`jetbrains/qodana-jvm-community`, perfil "Project Default") em Docker local para trazer as inspeções do IntelliJ para o laudo de erros e de warnings de cada card.

No REC-002 o Qodana **não chegou a rodar**:
- o container morreu por falta de memória (exit 137) durante a importação do projeto Gradle, depois de ~50 min;
- a VM do Docker Desktop da máquina de desenvolvimento tem ~3,8 GB e divide essa memória com outros projetos (um cluster `kind` usa ~2,4 GB), então sobra ~1,4 GB, abaixo do que o Qodana precisa para importar o monorepo;
- a partir do REC-003 os testes de integração usam Testcontainers (Postgres), que disputam o mesmo Docker.

Subir a memória do Docker local resolveria, mas o usuário prefere montar um **servidor Docker dedicado** mais adiante, sem disputar recursos com a máquina de desenvolvimento.

## Decisão

1. **O Qodana sai do fluxo de revisão dos cards** até existir o servidor Docker dedicado. Nenhum agente roda o Qodana na máquina local.
2. Enquanto isso, os laudos de erros e de warnings se apoiam no que já está no build: `javac -Xlint:all`, SpotBugs, Spotless, testes (unit + integração), JaCoCo, ArchUnit (a partir do REC-004), avisos do Gradle e WARN nos logs de teste, além da varredura manual.
3. A retomada é o **REC-075** (fora da contagem do MVP). Ele começa quando o usuário avisar que o servidor está pronto.

## Opções consideradas

- **Subir a memória do Docker Desktop (8–10 GB) e reiniciar.** Resolve no curto prazo, mas reinicia e disputa memória com os outros projetos da máquina. Recusada pelo usuário.
- **Parar o `kind` durante cada execução do Qodana.** Fica no limite de memória (~3,3 GB) e interfere em outro projeto a cada card. Recusada.
- **Adiar até um servidor Docker dedicado.** Escolhida.

## Consequências

- **Risco aceito:** inspeções que só o IntelliJ faz (nulidade, código suspeito, más práticas que o SpotBugs não pega) não entram nos laudos até o REC-075. Os cards feitos nesse intervalo, do REC-002 em diante, serão varridos de uma vez quando o Qodana voltar.
- O script `~/.cache/qodana-recorra/run.sh` fica na máquina, sem uso, até o REC-075. Ele está fora do repositório.
- Os papéis do caçador de erros e do revisor de warnings na bancada de agentes deixam de pedir o Qodana.

## Itens de ação

- [x] Retirar o Qodana dos papéis de revisão (2026-11-10).
- [ ] REC-075: com o servidor dedicado pronto, rodar o Qodana contra ele, varrer o histórico e religá-lo nas revisões.
