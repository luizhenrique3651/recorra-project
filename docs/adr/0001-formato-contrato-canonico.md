# ADR-0001: Formato do contrato canônico — JSON Schema como fonte única, Java gerado

**Status:** Aceita
**Data:** 2026-11-07
**Decisores:** Luiz Henrique (dev/arquiteto)
**Fecha:** decisão em aberto nº 1 do `CONTEXTO-PROJETO.md` (§5)
**Afeta:** REC-002 (Sprint 0), REC-010 (Sprint 1), todo driver futuro

---

## Contexto

O contrato canônico é a única ponte permitida entre o `core-api` e os drivers de ERP. Ele define o modelo (`ClienteCanonico`, `VendaCanonica`, ...), os eventos (`venda.canonica.registrada`, ...) e o comando `mensagem.enviar`.

Forças em jogo:

- **Core agnóstico.** O core nunca conhece um ERP; o contrato é o que garante isso. Ele precisa ser revisável fora do código Java.
- **Drivers de terceiros.** Os modelos de monetização 2 e 3 (marketplace do ERP, white-label/B2B2B) abrem a porta para drivers escritos por parceiros, possivelmente fora da JVM.
- **Transporte muda.** O MVP usa RabbitMQ; a escala prevê Kafka. O formato não pode depender do broker.
- **REC-002 já exige** que as classes Java sejam *geradas*, nunca escritas à mão.
- **Equipe de 1 pessoa, regime parcial.** O custo de ferramental precisa ser baixo.

## Decisão

1. **A fonte da verdade é JSON Schema (draft-07)**, um arquivo por tipo, em `contracts/src/main/schemas/`.
2. **AsyncAPI 3.0** (eventos/canais) e **OpenAPI 3.1** (ingestão síncrona) **referenciam** esses schemas via `$ref`. Eles documentam canais e endpoints, mas **não redefinem** formatos.
3. **Um único gerador produz o Java:** `jsonschema2pojo`, no build do módulo `contracts`, com saída em `build/generated/` adicionada ao sourceSet.
4. **Versionamento SemVer do contrato inteiro** (começa em `0.1.0` no REC-010):
   - campo opcional novo → **minor**;
   - remover/renomear campo, mudar tipo, tornar obrigatório → **major**;
   - consumidores são *tolerant readers*: ignoram campos desconhecidos.
5. **Validação contínua:** o CI valida os schemas contra o meta-schema e as fixtures (incluindo payloads reais vindos do REC-019) contra os schemas.
6. **Proibido no contrato:** campos específicos de ERP, lógica, e tipos escritos à mão. Identificadores externos (ex.: id da venda no ERP, usado na idempotência) entram como **string opaca** + código do ERP de origem, nunca com tipo ou formato de um ERP específico.

### Layout resultante (refina o REC-002)

```
contracts/
  src/main/schemas/
    modelo/      cliente-canonico.schema.json, venda-canonica.schema.json, ...
    eventos/     venda-canonica-registrada.schema.json, ...
    comandos/    mensagem-enviar.schema.json
  src/main/asyncapi/recorra.asyncapi.yaml     # canais + mensagens → $ref schemas/
  src/main/openapi/ingestao.openapi.yaml      # endpoints síncronos → $ref schemas/
  build/generated/sources/jsonschema2pojo/    # Java gerado (não versionado)
```

## Opções consideradas

### Opção A: jar Java compartilhado (records escritos à mão)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa |
| Custo | Nenhum ferramental extra |
| Escalabilidade (drivers de terceiros) | Ruim: amarra todo driver à JVM e ao nosso build |
| Familiaridade | Alta |

**Prós:** o mais simples possível; segurança de tipos imediata; refatoração pela IDE.
**Contras:** o contrato passa a *ser* código Java, então revisar o formato exige ler Java; não há artefato neutro para parceiros; contradiz o REC-002 ("nunca escritas à mão"); versionar o fio e versionar o jar viram a mesma coisa, e é fácil vazar um tipo do domínio.

### Opção B: OpenAPI como fonte + `openapi-generator` (só models)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média/Alta |
| Custo | Gerador pesado, com muitas opções de configuração |
| Escalabilidade | Boa para HTTP |
| Familiaridade | Média |

**Prós:** gerador maduro, com suporte a muitas linguagens.
**Contras:** OpenAPI descreve HTTP, e os eventos assíncronos (a maior parte do contrato) ficariam "encaixados" num documento que não é para eles; os models gerados têm muitas opiniões embutidas (nullable, validação, herança).

### Opção C: JSON Schema como fonte única + `jsonschema2pojo` ✅

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média |
| Custo | Um plugin/task de geração + validação no CI |
| Escalabilidade | Boa: qualquer linguagem gera tipos a partir de JSON Schema |
| Familiaridade | Média (JSON Schema é simples; AsyncAPI é novidade) |

**Prós:** um formato só, legível e neutro; serve tanto para eventos (AsyncAPI) quanto para HTTP (OpenAPI 3.1, que é compatível com JSON Schema); os parceiros recebem os `.schema.json` e geram os próprios tipos; o mesmo schema valida as fixtures do Bling.
**Contras:** o `jsonschema2pojo` gera POJOs mutáveis (não records); o draft-07 é um subconjunto; há um passo de build a mais.

### Opção D: Avro ou Protobuf + schema registry

| Dimensão | Avaliação |
|---|---|
| Complexidade | Alta |
| Custo | Registry para operar; payload binário |
| Escalabilidade | Excelente em Kafka |
| Familiaridade | Baixa |

**Prós:** regras de evolução fortes e verificadas; payload compacto.
**Contras:** exagero para RabbitMQ no MVP; depurar payload binário é pior; os ERPs entregam JSON de qualquer jeito. **Reavaliar na migração para Kafka.**

## Análise de trade-offs

A decisão real é **onde mora a forma do dado**, não qual gerador usar. A opção A é mais rápida no Sprint 0, mas fecha a porta dos drivers de terceiros, e essa porta é justamente o que sustenta os modelos de monetização 2 e 3. A opção C custa algumas horas a mais no REC-002 e mantém o contrato neutro.

Escolhemos **draft-07** (e não 2020-12) porque é o que o `jsonschema2pojo` cobre bem e é o formato padrão de schema do AsyncAPI. O OpenAPI 3.1 aceita `$ref` para esses arquivos sem problema.

O gerador é **substituível**: se o `jsonschema2pojo` travar (ver riscos), trocamos para o `openapi-generator` lendo o mesmo OpenAPI que já referencia os mesmos schemas. A fonte da verdade não muda.

## Consequências

**Fica mais fácil**
- Revisar o contrato sem ler Java (diff de `.schema.json` no PR).
- Entregar o contrato a um parceiro: zip dos schemas + AsyncAPI renderizado.
- Testes de contrato: fixtures reais do Bling validadas contra o schema canônico.
- Trocar o broker (RabbitMQ → Kafka) sem mexer no formato.

**Fica mais difícil**
- Os POJOs gerados são menos idiomáticos que records. No core, mapear para o domínio logo na borda (o que a arquitetura hexagonal já pede).
- Toda mudança de contrato passa pelo schema, que é intencionalmente mais lento que editar uma classe.

**Riscos a verificar no REC-002**
- **Gradle 9:** confirmar se o plugin Gradle do `jsonschema2pojo` funciona no Gradle 9. Se não funcionar, usar uma task própria no `build-logic` chamando o `jsonschema2pojo-core` diretamente.
- **Jackson 3 (Spring Boot 4, ver ADR-0002):** as anotações `com.fasterxml.jackson.annotation.*` continuam válidas no Jackson 3, mas anotações de *databind* (ex.: `@JsonDeserialize`, que o gerador emite para arrays com `uniqueItems`) mudaram de pacote. Evitar `uniqueItems` e `additionalProperties` no schema, e garantir que o teste de round-trip do REC-002 roda com o `ObjectMapper` do Boot 4.

**A revisitar**
- Avro/Protobuf + registry quando migrarmos para Kafka.
- Validação de schema em runtime na borda de ingestão do core (pós-MVP).

## Itens de ação

1. [x] REC-002: criar o layout acima e um schema mínimo de `venda.canonica.registrada`; gerar o Java e fazer round-trip com o `ObjectMapper` do Boot 4. — feito em 2026-11-10 (REC-002: layout, schema mínimo e round-trip com o mapper do Boot 4).
2. [x] REC-002: provar a geração no Gradle 9 (plugin oficial ou task própria). — feito em 2026-11-10 (REC-002: geração provada no Gradle 9.2.1 com o plugin oficial `org.jsonschema2pojo` 1.3.3; sem task própria).
3. [ ] REC-003: no CI, validar schemas e fixtures (ex.: `networknt/json-schema-validator`).
4. [ ] REC-010: fechar todos os tipos/eventos, documentar a política SemVer no `contracts/README.md` e taguear `contracts-v0.1.0`.
5. [x] Atualizar o `CONTEXTO-PROJETO.md` §3/§5: decisão nº 1 fechada.

## Notas de alteração

- 2026-11-08 — o transporte do MVP passou a ser HTTP de ingestão + outbox (REC-043); a menção a RabbitMQ acima fica como contexto da época. A decisão desta ADR não muda.
