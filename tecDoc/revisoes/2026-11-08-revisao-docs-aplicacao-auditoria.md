# Revisão de docs — aplicação da auditoria 2026-11-08

*Tech lead · 2026-11-08 · Branch `chore/roadmap-alinhamento-produto` · Referências `:NNN` = linha de `tecDoc/roadmap-desenvolvimento-sdd.md`, salvo indicação.*

**Veredito: REPROVADO.** As correções são pontuais e não exigem refazer a estrutura.

A cobertura do laudo está completa: C1–C5, I1–I7, E1–E6, D1–D5 e D7 foram aplicados, com critérios e cenários de I2 em REC-031/040/041/046.
- **SP:** S0 25 · S1 24 · S2 21 · S3 21 · S4 22 · S5 24 · S6 23 · S7 21 · S8 21 · S9 19 = 221. Bate com os cabeçalhos e com o apêndice.
- **Reconciliação:** 190 (soma real do plano anterior) + 49 (cards novos) − 18 (líquido entre cortes e ajustes) = 221.
- **IDs:** nenhum colide. REC-052 aparece como cortado e REC-073 foi para a Fase 2.
- **Formato:** os cards novos seguem o padrão.

O que reprova: duas inversões de dependência, uma colisão de migration, regras decididas sem a marca ⚠️ e três buracos de log (obrigatórios pela regra do usuário).

## Correções obrigatórias (dev)

1. **Colisão e ordem de migration Flyway.** :771 (REC-035, S3) usa V0302 e :809 (REC-030, S4) usa V0301. O Flyway não aplica versão menor depois de maior (validate falha sem outOfOrder). :908 (REC-046) e :1148 (REC-055) não têm número.
   → Renumerar pela ordem de execução: S3 consentimento = V0301; S4 inteligência = V0401, elegibilidade = V0402; S5 ação = V0501, outbox = V0502; S6 whatsapp = V0601. Registrar a convenção `V<sprint><seq>` no REC-003.

2. **Transporte driver→core decidido tarde demais.** :1030 põe o REC-043 no S5, mas :738 (REC-022, S3) e :679 (REC-023, S2) já entregam eventos de outro processo ao core. O driver-bling é app separado, e o "adaptador in-process" de :609 não atravessa processos. O apêndice :1498 ("REC-042+REC-043 → REC-020, REC-022") tem a seta invertida em relação aos sprints.
   → Mover o REC-043 para o S2, antes do REC-023, ou dizer explicitamente que S2–S4 usam a Opção A (HTTP de ingestão) como interina e que o REC-043 só formaliza ou troca. Corrigir :1498 e o gate de :701. A decisão do transporte passa a bloquear o S2.

3. **Teto de envio usado antes de existir.** :1117 (REC-053, S6) consulta o teto do REC-056, que está no S7 (:1229). O apêndice :1500 mostra REC-056 → REC-053.
   → Mover o REC-056 para o S6 (ou o REC-053 para o S7) e rebalancear os SP. No S6, ordenar o REC-055 antes do REC-051, que depende dele.

4. **Consentimento chaveado por cliente × opt-in de telefone sem cliente.** :771 define o `consentimento` como "estado atual por cliente", mas :1186 (REC-054) exige consentimento vinculado ao telefone antes de o cliente chegar do ERP.
   → No REC-035, chave `(comercio_id, telefone_e164)` (índice cego se a ADR-0004 escolher B ou C), com `cliente_id` opcional associado depois. Cenário no REC-035, não só no 054.

5. **ConsentPolicy conhecendo regra da Meta.** :773 faz a ConsentPolicy (core) informar a janela de 24h da Meta. Contradiz :1069/:1071 (core não conhece WhatsApp) e duplica o REC-051 (:1086).
   → Tirar a janela de 24h da ConsentPolicy: ela decide só opt-in + telefone válido; template × sessão fica no adapter.

6. **Ação PENDENTE que nunca vence.** :1151 (template pendente) e :1237 (teto atingido) mantêm a ação PENDENTE sem prazo; quando o bloqueio cai, ações fora da janela seriam enviadas atrasadas. Em :944, EXPIRADA só cobre a recompra.
   → Em :944 e no §1.4 (:70): "PENDENTE além de `proxima_prevista + tolerancia_atraso_dias` sem envio → EXPIRADA", com cenário no REC-040 e no REC-056. Marcar ⚠️ como regra proposta.

7. **Cenários que faltam para provar I2.** REC-040 (:949–954) não testa CANCELADA por venda cancelada nem por produto que ficou inelegível, embora :944 defina os dois. REC-046 (:910) define o cancelamento ao mudar a flag, mas :916–919 não testam.
   → Dois cenários no REC-040 e um no REC-046.

8. **Regras inventadas ou escolhas tomadas sem ⚠️.** Marcar todas como "⚠️ valor/regra proposto, confirmar":
   - :574 `fuso_horario` default America/Sao_Paulo (o piloto pode ser em Manaus, UTC−4);
   - :575 `tolerancia_atraso_dias` = janela_n_dias;
   - :579 sem teto configurado = envio bloqueado;
   - :973 teto escolheu LIMITAR por ordem de vencimento (o laudo deixou "consolidar OU limitar"; a consolidação foi para a Fase 2 sem decisão);
   - :645 dedup de cliente por telefone (telefone compartilhado por uma família funde o histórico e distorce os ciclos);
   - :1270 desempate "mais recente" e não reversão da conversão por cancelamento;
   - :1151 template pendente = PENDENTE, e não FALHA.

9. **Logs e rastreabilidade (regra do usuário, obrigatório).**
   - a) :451 (REC-011, jobs) e :609 (consumers) não dizem que cada unidade de trabalho por comércio abre MDC `comercioId` + `traceId` novo (job) ou restaurado (consumer) e limpa ao final. → Incluir no REC-005 (:353) e no REC-011, com cenário "job com A e B → logs de A só com comercioId=A".
   - b) :709 (REC-021): o callback OAuth `GET /callback?code=&state=` vaza o `code` em access log e log de request. → REC-005 mascara ou omite a query string de rotas OAuth (`code`, `state`, `token`), com teste de appender.
   - c) :1189 (REC-054): endpoint público com rate limit por IP; IP é dado pessoal. → Não logar o IP completo (truncar ou hash) nem o telefone nos logs de rate limit e rejeição; incluir no NÃO FAZER e nos cenários.
   - d) :1038 (REC-043 A, tabela de falhas de ingestão) e DLQ (B): gravar `traceId` + classe e motivo do erro em cada falha. Payload com PII só conforme a ADR-0004/retenção. Critério e cenário.

10. **Pendências de texto.**
    - :234 (REC-001) lista o settings sem wa-terceiro, mas chama a remoção de "ajuste separado", e a ADR-0003 (:28/:57) a deixa "para o card que tocar o build" sem card dono → atribuir a remoção do `messaging:wa-terceiro` (settings + README) ao REC-001.
    - CONTEXTO-PROJETO.md:49 diz "só há ciclo com ≥3 compras", mas o roadmap (:57/:831) diz que 2 compras = ciclo de baixa confiança, que não gera ação → alinhar.
    - CONTEXTO :50 sem a tolerância de atraso → alinhar com o §1.4 (:64).
    - CONTEXTO :127 "Estamos em pré-desenvolvimento" → "Sprint 0".
    - ADR-0004 :19 e :70 citam "S7" para retenção/exclusão; agora é S9 (REC-072).
    - ADR-0001 :132 e ADR-0002 :56 têm o item "atualizar CONTEXTO" em `[ ]`, mas já foi feito → `[x]`.

## Sugestões (opcionais)

- O opt-in só acumula depois que a landing (REC-054, S7) estiver publicada, e o deploy é no S9: o piloto entra com base quase vazia. Avaliar deploy mínimo da landing logo após o REC-054, ou antecipar o card (pode virar decisão do usuário).
- REC-064 Opção B (e-mail) exige provedor de e-mail (SES/SMTP), ausente do REC-074 (:1437) → incluir na infra e no runbook.
- ENVIADA sem conversão fica ENVIADA para sempre; vale um estado ou relatório "não convertida" no fim da janela X (Fase 2).
- IDs novos fora da faixa do sprint (REC-035 no S3, REC-046 no S4): explicar a convenção no §3.

## Decisões pendentes do usuário (por prioridade)

**Bloqueiam os próximos sprints**
1. [S1, antes do REC-020] ADR-0004 — criptografia de PII: opção A/B/C, colunas, escopo da chave e busca por telefone (REC-014). Gate do S2.
2. [S1] REC-015 — montagem do wa-cloud e do comando `mensagem.enviar` (padrão: Opção A). Afeta o contrato v0 (REC-010).
3. [S2] REC-043 — transporte driver→core (HTTP+outbox × RabbitMQ).
4. [S2, gate] F3 + F5 — análise do export do Bling do candidato; beachhead e comerciante-piloto.
5. [S2, REC-013/024] Defaults de negócio: fuso, tolerância de atraso, corte de inativo (2×), teto semanal (1) e limitar × consolidar, janela de atribuição (7), dedup de cliente por telefone, expiração de PENDENTE.
6. [Abrir agora, prazo longo] F1 suporte do Bling (gate do REC-022/S3); F2 Meta: Business verification, WABA e template (gate do S6/piloto); F4 papel LGPD + DPA (gate do piloto e do texto da landing).

**Podem esperar (antes do sprint indicado)**
7. [S6] REC-055 — conexão à Meta: cadastro manual × Embedded Signup.
8. [S7] REC-054 — estratégia de captação na loja e momento do deploy da landing.
9. [S7] REC-056 — quem paga a mensagem.
10. [S8] REC-064 — resumo semanal por e-mail × UI React (+5 SP).
11. [S9] REC-074 — plataforma de deploy (VM/ECS/PaaS) e IdP do REC-012 (Keycloak × Cognito).
12. [S9] Retenção e exclusão (ADR-0004 item 5, REC-072).
13. [Sem prazo técnico] Nome/marca do produto (CONTEXTO §5.5).

> A pendência "instalar JDK 21" da revisão original foi descartada: o JDK 21 existe via SDKMAN e já está nas regras da bancada.
