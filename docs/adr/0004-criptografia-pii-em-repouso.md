# ADR-0004: Criptografia de PII em repouso e papéis LGPD

**Status:** Aceita (Opção B, decidida em 2026-11-08)
**Data:** 2026-11-08
**Decisores:** Luiz Henrique (dev/arquiteto)
**Origem:** auditoria de 2026-11-08, achado I5
**Afeta:** REC-014 (decisão), REC-016 (componente de criptografia), REC-020 (schema de `cliente`), REC-021 (tokens de ERP), REC-024 (dedup), REC-035 (consentimento), REC-054 (captação de opt-in), REC-055 (credenciais da Meta), REC-072 (LGPD), REC-074 (KMS/Secrets Manager)

> **Decisão (2026-11-08): Opção B.** A ADR está "Aceita", o que destrava o REC-020. As Opções A e C ficam registradas como alternativas descartadas. Continuam pendentes o termo/DPA com o comércio e a política de retenção (ver Itens de ação).

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

### Opção B (ESCOLHIDA): criptografia por coluna na aplicação (AES-GCM, chave por comércio ou global) + índice cego (HMAC) para busca por telefone

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

## Decisão

**Opção B — criptografia por coluna na aplicação (AES-GCM) + índice cego (HMAC) no telefone**, decidida pelo usuário em 2026-11-08:

1. **Colunas cifradas:** telefone, CPF, e-mail e **nome**.
2. **Índice cego (HMAC)** no telefone, para busca e deduplicação (REC-024); é também a chave do consentimento (REC-035: `comercio_id` + hash do telefone).
3. **Chave por comércio**, sob uma **chave mestra**. No piloto a mestra fica em **variável de ambiente**; no deploy definitivo, em **KMS/Secrets Manager** (REC-074).
4. A chave por comércio **permite** excluir os dados de um comércio descartando a chave. **O modo de exclusão (de comércio e de cliente) e a retenção NÃO são decididos aqui: ficam para o REC-072 (S9).**
5. As Opções A (volume/banco) e C (`pgcrypto`) ficam descartadas.

## Esquema técnico (parâmetros mínimos; derivados da decisão, para o REC-016/REC-020)

> Estes parâmetros detalham a Opção B para que o schema do REC-020 nasça correto. Não são decisões novas do usuário.

- **Envelope:** a chave por comércio é uma **DEK aleatória** (não derivada de forma determinística da mestra), gerada na criação do comércio e guardada **cifrada pela mestra**. Uma chave derivada de mestra + `comercio_id` poderia ser derivada de novo e não haveria o que descartar; com a DEK, descartar = apagar a DEK. (Alternativa equivalente: sal aleatório por comércio na derivação.)
- **Chaves separadas para AEAD e para MAC:** dois keysets Tink por comércio (AEAD e MAC), ambos cifrados pela mestra. Nunca a mesma chave para cifrar e para indexar.
- **Nonce** aleatório de 96 bits por operação de cifra, sem reuso.
- **AAD** (dados autenticados adicionais) com `comercio_id` + nome da coluna.
- **Versão da chave** no ciphertext (prefixo) ou em coluna `chave_versao`.
- **Rotação do HMAC:** recalcular `telefone_hash` em `cliente` **e** em `consentimento` (e na trilha) **no mesmo job**, com o `unique` ativo. O job fica fora do REC-016 (só desenho na `spec.md`); a versão no ciphertext permite a rotação futura.
- **Implementação:** Google Tink (AEAD + keyset em envelope) no REC-016, em vez de AES-GCM/HKDF montados à mão; o esquema acima é o contrato, o Tink é o meio.

## Consequências

- **Schema (REC-020):** colunas `nome_cifrado`, `telefone_cifrado`, `cpf_cifrado`, `email_cifrado`, `telefone_hash` e a versão da chave; a unicidade por telefone vira `unique (comercio_id, telefone_hash)`.
- **Dedup (REC-024):** pelo índice cego do telefone E.164. O risco do telefone compartilhado (funde históricos) fica documentado como **limitação conhecida**.
- **Consentimento (REC-035):** chave `(comercio_id, telefone_hash)`; a landing e a importação (REC-054) usam o mesmo normalizador E.164 e o mesmo `BlindIndex`.
- **Custo:** gestão de chaves e rotação (re-cifra); relatórios, listagens e dashboards decifram na aplicação (só a página retornada); busca só por igualdade de telefone; sem filtro, ordenação ou SQL sobre nome, CPF ou e-mail.
- **Limitação do descarte:** com a DEK cifrada no próprio Postgres e a mestra ainda viva, um backup do banco segue legível até expirar. Para o descarte valer também sobre backups, as DEKs devem ficar **fora dos backups do banco** (KMS, REC-074).
- **Risco aceito no piloto:** a mestra em variável de ambiente é mais fraca que KMS; vale só até o deploy definitivo.
- **Consequências derivadas (não são decisões do usuário):**
  - o mesmo esquema de cifra por comércio é usado nos **tokens do ERP** (REC-021) e nas **credenciais da Meta** (REC-055);
  - o **HMAC é por comércio** (keyset MAC do comércio, no envelope sob a mestra): um HMAC global permitiria correlacionar telefones entre comércios e anularia o descarte da chave.
- **Segue valendo:** RLS por `comercio_id` (REC-011) como barreira de isolamento; PII nunca em log (REC-005), incluindo a mestra, as DEKs e os keysets AEAD/MAC.

## Itens de ação

1. [x] Usuário escolhe a opção e atualiza o Status para "Aceita" (REC-014) — feito em 2026-11-08.
2. [x] Refletir a decisão no REC-016 (componente), REC-020 (schema de `cliente`), REC-024 (dedup), REC-035, REC-054, REC-021/REC-055 e REC-072 — registrado no roadmap em 2026-11-08.
3. [ ] Redigir o termo/DPA com o comércio (frente externa F4) — **pendente**.
4. [ ] Política de retenção e **modo de exclusão (de comércio e de cliente)** (REC-072, S9) — **pendente, por decisão do usuário de não fixar agora**.
5. [ ] Chave mestra em KMS/Secrets Manager (REC-074).
