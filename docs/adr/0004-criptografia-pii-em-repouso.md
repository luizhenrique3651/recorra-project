# ADR-0004: Criptografia de PII em repouso e papéis LGPD

**Status:** Proposta — ⚠️ DECISÃO PENDENTE DO USUÁRIO
**Data:** 2026-11-08
**Decisores:** Luiz Henrique (dev/arquiteto) — a decidir
**Origem:** auditoria de 2026-11-08, achado I5
**Afeta:** REC-014 (decisão), REC-020 (schema de `cliente`), REC-021 (tokens de ERP), REC-055 (credenciais da Meta), REC-072 (LGPD)

> Esta ADR registra as opções e **não decide**. O REC-020 não começa até ela virar "Aceita", porque a escolha muda o schema de `cliente` (S2).

---

## Contexto

O MVP guarda PII de clientes finais do comércio: nome, WhatsApp (telefone), e opcionalmente CPF e e-mail. Também guarda segredos: tokens OAuth do ERP (REC-021) e credenciais da Meta por comércio (REC-055).

A auditoria (I5) observa que:

- Criptografar PII em repouso muda o schema, então a decisão precisa vir antes do REC-020, e não no S9 (REC-072).
- Retenção e exclusão podem ficar para o S9 (REC-072).
- Falta definir o papel LGPD: o **comércio é o controlador** e a plataforma é a **operadora**. Falta o **termo/DPA** com o comércio, que é pré-requisito do piloto.

Pontos que a decisão precisa respeitar:

- O telefone é usado como chave de busca e deduplicação (REC-024, E.164). Cifrar a coluna impede busca por igualdade, a não ser que exista um índice cego (*blind index*).
- A RLS por `comercio_id` (REC-011) continua sendo a barreira de isolamento. A criptografia é uma camada adicional, contra vazamento de disco, backup e acesso do DBA.
- PII nunca em log (REC-005).
- Equipe de 1 pessoa, regime parcial: o custo operacional importa.

## Opções consideradas

### Opção A: criptografia de disco/volume e do banco gerenciado, sem criptografia por coluna

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa |
| Protege contra | Perda de disco/snapshot/backup |
| Não protege contra | Acesso lógico ao banco (SQL, dump em claro, DBA) |

**Prós:** nenhum impacto no schema nem nas queries; zero código. Postgres gerenciado já oferece.
**Contras:** o conteúdo da tabela fica em claro para quem consegue ler o banco; pode ser insuficiente para a expectativa de "PII cifrada em repouso" do REC-072.

### Opção B: criptografia por coluna na aplicação (AES-GCM, chave por comércio ou global) + índice cego (HMAC) para busca por telefone

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média/Alta |
| Protege contra | Disco, backup, dump e acesso lógico ao banco |
| Custo | Gestão de chaves (KMS/Secrets Manager), rotação, colunas `*_cifrado` e `*_hash` |

**Prós:** o banco nunca vê PII em claro; atende bem à LGPD; chave por comércio facilita exclusão (descartar a chave).
**Contras:** busca e `unique` passam a depender do índice cego; relatórios e dashboards que usam PII precisam decifrar na aplicação; rotação de chave exige re-cifra; mais código e testes no S2.

### Opção C: `pgcrypto` (cifra no banco) por coluna

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média |
| Protege contra | Disco e backup; **não** protege contra quem lê o banco e a chave junto |

**Prós:** sem código de aplicação para cifrar.
**Contras:** a chave trafega nas queries (aparece em log de statement e no `pg_stat_statements` se mal configurado); mesma dependência de índice cego para busca; acopla ao Postgres.

## Pontos que o usuário precisa decidir

1. Qual opção (A, B ou C), e em quais colunas: só `telefone`/`cpf`/`email`, ou também `nome`?
2. Se B ou C: chave global ou por comércio? Onde mora a chave (Secrets Manager, KMS, variável de ambiente no piloto)?
3. Como o telefone é buscado e deduplicado (índice cego, ou telefone em claro e só CPF/e-mail cifrados)?
4. Papel LGPD e **termo/DPA** com o comércio: quem redige e revisa (jurídico), e em que data fica pronto. É pré-requisito do piloto e não é código.
5. Retenção e exclusão (REC-072, S9): prazo de retenção e se a exclusão é anonimização ou remoção.

## Consequências (a preencher quando decidida)

- Pendente.

## Itens de ação

1. [ ] Usuário escolhe a opção e atualiza o Status para "Aceita" (REC-014).
2. [ ] Refletir a decisão no REC-020 (schema de `cliente`) e no REC-024 (deduplicação por telefone).
3. [ ] Redigir o termo/DPA com o comércio (frente externa).
