# ADR-0003: WhatsApp — só a API oficial (Meta Cloud API) no MVP

**Status:** Aceita
**Data:** 2026-11-08
**Decisores:** Luiz Henrique (dev/arquiteto) — decisão do usuário de aplicar a auditoria de 2026-11-08 (achado C5)
**Fecha:** decisão em aberto nº 3 do `CONTEXTO-PROJETO.md` (§5.3)
**Afeta:** REC-050, REC-051, REC-052 (cortado), REC-055; `messaging/wa-terceiro`

---

## Contexto

O plano original previa dois adapters atrás do `MessagingPort`: o oficial (WhatsApp Cloud API, da Meta) e um "terceiro" (API não oficial, com número dedicado), este último mais barato e mais arriscado (REC-052).

A auditoria produto × plano técnico (`tecDoc/revisoes/2026-11-08-auditoria-produto-x-plano-tecnico.md`, achado C5) aponta que o adapter de terceiro é um risco de produto e jurídico:

- Uma API não oficial viola os termos de uso do WhatsApp.
- O número usado pode ser banido, e o comércio perde o canal sem aviso.
- Isso contradiz a invariante nº 6 do roadmap (§1.6): "WhatsApp com regras da Meta".
- O produto envia mensagem de marketing para clientes do comércio (LGPD, consentimento). Operar fora dos termos do canal agrava o risco do controlador, que é o comércio.

## Decisão

1. **O MVP usa somente a WhatsApp Cloud API oficial (Meta).** O adapter é o `messaging/wa-cloud` (REC-051).
2. **O REC-052 (adapter `wa-terceiro`) sai do MVP** (−5 SP). Não vai para o backlog da Fase 2 sem uma nova ADR que reavalie o risco.
3. **A porta `MessagingPort` continua** (REC-050). Ela mantém o core desacoplado de provedor e permite, no futuro, outro provedor **oficial** (ex.: um BSP credenciado pela Meta) sem mexer no core.
4. `messaging_provider` na config do comércio (REC-050) continua existindo, mas com um único valor válido no MVP (`oficial`).
5. O módulo `messaging/wa-terceiro` não deve receber código. A remoção dele do `settings.gradle.kts`, do diretório e do README é escopo do **REC-001**.

## Opções consideradas

| Opção | Prós | Contras |
|---|---|---|
| **Só oficial ✅** | Dentro dos termos da Meta; número não é banido por uso de API não oficial; coerente com a invariante nº 6 | Custo por mensagem de marketing (~R$0,37); exige Business verification, WABA, número e template aprovado por comércio (cobertos por REC-055 e REC-056) |
| Oficial + terceiro (plano original) | Alternativa mais barata | Viola termos do WhatsApp; risco de banimento do número; risco jurídico; +5 SP de adapter e de documentação de risco |
| Só terceiro | Mais barato e rápido de começar | Os mesmos riscos, sem alternativa segura |

## Consequências

**Fica mais fácil**
- Uma única integração para testar e operar no MVP.
- O discurso comercial e o termo/DPA com o comércio não precisam explicar o risco de banimento.
- −5 SP no plano.

**Fica mais difícil**
- O custo por mensagem existe desde o piloto, e ainda não está definido quem paga. O REC-056 cobre teto e métrica de custo, mas a decisão comercial continua com o usuário.
- O onboarding da Meta (Business verification, WABA, número, aprovação de template) tem prazo longo e passa a ser caminho crítico do piloto. Ele deve ser aberto fora do board, agora (ver "Frentes externas" no roadmap).
- A Meta limita mensagens de marketing por usuário, então a entrega não é garantida. O status "não entregue por política" é tratado como `FALHA`, sem reenvio (REC-056).

**A revisitar**
- Um BSP oficial, se o onboarding por comércio ficar caro demais (Embedded Signup, REC-055).

## Itens de ação

1. [x] Cortar o REC-052 do roadmap e registrar o motivo.
2. [x] Atualizar `CONTEXTO-PROJETO.md` §3 e §5.3.
3. [ ] Remover `messaging:wa-terceiro` do `settings.gradle.kts`, do diretório e do README (**dono: REC-001**).
4. [ ] Abrir o processo de Business verification da Meta (frente externa, usuário).
