# Decisões do usuário — Sprints 1 e 2 (2026-11-08)

Lista curta das decisões tomadas hoje e onde foram refletidas.

## Decididas

1. **ADR-0004 (PII) — Opção B.** Criptografia por coluna na aplicação (AES-GCM) em telefone, CPF, e-mail e nome; índice cego (HMAC) no telefone; **DEK por comércio em envelope sob a mestra** (variável de ambiente no piloto, KMS/Secrets Manager no REC-074); **a chave por comércio PERMITE o descarte; o modo de exclusão e a retenção ficam no REC-072**. ADR "Aceita". Refletida em REC-014, 016, 020, 021, 024, 035, 055, 072.
2. **REC-015 — Opção A.** `MessagingPort` in-process; `wa-cloud` só `runtimeOnly` no `core-api` (ArchUnit); webhook no `core-api`; `mensagem.enviar` fora do contrato. Alinhados REC-010, 050, 053.
3. **REC-043 — Opção A.** HTTP de ingestão idempotente + outbox. RabbitMQ só como registro histórico/alternativa futura. Alinhados REC-020, 022, 023, 042 (e REC-074, e2e, hardening).
4. **Defaults de negócio (configuráveis por comércio):** N=5; tolerância de atraso=N; inativo após 2 ciclos sem compra; 1 ação/cliente/semana limitando pela ordem de vencimento (consolidar = Fase 2); janela de atribuição 7 dias; dedup de cliente pelo telefone via índice cego (telefone compartilhado = limitação conhecida); PENDENTE além de `proxima_prevista`+tolerância → EXPIRADA; sem teto configurado = envio bloqueado; template pendente = PENDENTE; desempate "mais recente" e sem reversão de conversão.

5. **(A) Outbox do driver guarda só a referência** (`erp_venda_id` + tipo); na reentrega o driver busca o pedido de novo no Bling, respeitando o rate limit; sem PII em repouso no driver. REC-043 e REC-022.
6. **(B) Credencial do driver = segredo/HMAC por driver** (assinatura da requisição), independente do IdP; segredo em variável de ambiente no piloto e no gestor de segredos no REC-074; mascarado em log. O outbox durável vira peça do `driver-sdk`. REC-043 e REC-012.
7. **(C) REC-015 concluído** (decisão de 2026-11-08), fora da conta do S1.

**Estimativas:** REC-016 novo, +5 SP (S1; com Tink e sem o job de rotação do HMAC); REC-043 3→5 SP (S2); REC-015 −1 SP (concluído). **SP por sprint:** S0 25 · S1 28 · S2 26 · S3 21 · S4 22 · S5 21 · S6 23 · S7 21 · S8 21 · S9 19 = **227 SP** (232 com a UI React do REC-064).

## Continuam pendentes

- **Fuso:** provisório `America/Sao_Paulo`; a definir com o piloto (F5).
- F3 (dados do comerciante-candidato) e F5 (beachhead/piloto).
- IdP, conexão à Meta, estratégia de opt-in, quem paga, UI × resumo, plataforma de deploy.
- Dentro da ADR-0004: DPA com o comércio (F4) e política de retenção (REC-072, S9).
