# REC-002 · Esqueleto do `contracts` com geração de código a partir de schema

Card: `tecDoc/roadmap-desenvolvimento-sdd.md` §REC-002 · EP-03 · 5 SP · Sprint 0. ADR: 0001 (JSON Schema como fonte única, `jsonschema2pojo` como único gerador).

## Contratos

Sem endpoint de negócio. O que o card entrega é o **pipeline** de geração e UM evento de exemplo.

Layout (ADR-0001), tudo em `contracts/src/main/`:

| Caminho | Conteúdo |
|---|---|
| `schemas/modelo/venda-canonica.schema.json` | modelo mínimo da venda (só para o exemplo) |
| `schemas/eventos/venda-canonica-registrada.schema.json` | evento `venda.canonica.registrada` (envelope + `$ref` ao modelo) |
| `asyncapi/recorra.asyncapi.yaml` | AsyncAPI 3.0: canal + mensagem, `$ref` ao schema do evento |
| `openapi/ingestao.openapi.yaml` | OpenAPI 3.1: `POST /ingestao/eventos`, `$ref` ao schema do evento |

`schemas/comandos/` fica para o REC-010 (e, pela decisão do REC-015, `mensagem.enviar` nem entra no contrato).

Regras dos schemas (draft-07): sem `uniqueItems` e sem `additionalProperties` (ADR-0001, Jackson 3); identificador do ERP é `string` opaca + `erp` (código de origem), nunca tipo/formato de um ERP; nenhum campo específico de Bling; sem lógica.

Payload mínimo do exemplo (`venda.canonica.registrada`):

```json
{
  "evento": "venda.canonica.registrada",
  "versao_contrato": "0.1.0",
  "comercio_id": "<uuid>",
  "ocorrido_em": "2026-11-09T12:00:00Z",
  "venda": { "erp": "erp-exemplo", "erp_venda_id": "123", "data_venda": "2026-11-09T11:58:00Z", "valor_total": 99.90 }
}
```

AsyncAPI e OpenAPI **só referenciam** (`$ref`) os schemas e não redefinem formato (teste automatizado abaixo). O contrato completo (cliente, produto, itens, demais eventos, SemVer, tag `contracts-v0.1.0`) é o REC-010; aqui o `0.1.0` do campo `versao_contrato` é só valor de exemplo.

## Geração (decisão registrada)

- **Gerador:** somente `jsonschema2pojo`. Saída em `contracts/build/generated/sources/jsonschema2pojo/` (não versionada, dentro de `build/`), adicionada ao sourceSet `main` pelo próprio plugin.
- **Plugin oficial no Gradle 9: FUNCIONOU.** `org.jsonschema2pojo` **1.3.3** com Gradle 9.2.1 gera e compila (task `generateJsonSchema2Pojo`, ligada ao `compileJava`). O plugin é aplicado por uma convenção do `build-logic` (`recorra.contracts-conventions`), com `jsonschema2pojo-gradle-plugin` e `jsonschema2pojo-core` como dependências do `build-logic`: aplicá-lo direto no `contracts/build.gradle.kts` (`plugins { alias(...) }`) criava um classloader separado e quebrava o serviço do Spotless ("applied to two sibling projects"). **Não foi preciso** a task própria no `build-logic` com o `jsonschema2pojo-core`; esse é o plano B e não existe no repositório. O `openapi-generator` segue como plano B documentado na ADR-0001, sem uso.
- Versões no `gradle/libs.versions.toml` (`jsonschema2pojo` 1.3.3, `jackson-annotations` e `jackson-databind` 3 geridos pelo BOM do Boot 4.1.1).
- **Pacotes:** tudo em `com.recorra.contracts` (plano). A fonte é a lista ordenada `schemas/modelo`, `schemas/eventos`: com a pasta única, o tipo referenciado por `$ref` caía no pacote de quem o referenciou primeiro (`eventos.VendaCanonica`). O REC-010 acrescenta `comandos` ao fim da lista. O diretório gerado é apagado antes de cada geração (sem classe órfã).
- Opções: pacote único `com.recorra.contracts` (as pastas `modelo`/`eventos` não viram subpacotes; ver "Pacotes"); `annotationStyle=jackson2` (anotações `com.fasterxml.jackson.annotation`, que o Jackson 3 continua usando); `useBigDecimals`; datas-hora como `java.time.OffsetDateTime`; sem `additionalProperties`; `includeHashcodeAndEquals` ligado; **`includeToString` desligado de propósito** (o `toString` gerado imprime todos os campos e vazaria PII em log, REC-005).
- Dependências: `contracts` expõe `api(jackson-annotations)`; `core-api` e `driver-bling` usam `implementation(project(":contracts"))`. O `contracts` **não** depende de nenhum dos dois (ArchUnit do REC-004 amarra o resto).

## Modelo tocado

Nenhum (sem domínio, sem banco).

## Gate do JaCoCo sem vacuidade (débito do REC-001)

Alterações em `build-logic/src/main/kotlin/recorra.java-conventions.gradle.kts`:

1. **(a) Exclusão do código gerado.** A base do relatório e da verificação (`classDirectories`) exclui os `.class` cujo `.java` está sob `build/generated/` (inclui classes internas `X$*.class`). Sem isso, as classes do `jsonschema2pojo` (sem teste próprio) puxariam a cobertura do módulo para baixo ou, pior, inflariam a base.
2. **(b) Falha em vez de pular.** Nova task `verificarDadosDeCobertura`, ligada ao `check`: se o módulo tem classes de produção (após a exclusão (a)) e **não há arquivo de execução do JaCoCo** (nenhum teste rodou), o build **falha** com mensagem explicativa. Antes, o `jacocoTestCoverageVerification` era *pulado* ("missing execution data") e o módulo passava com 0% sem ninguém notar. Módulo sem classes de produção (só `package-info`) continua passando.

**Prova (teste):** `build-logic/src/test/.../JacocoGateTest` (Gradle TestKit) monta projetos temporários que aplicam o `recorra.java-conventions` e verifica:
- classe de produção sem teste → `check` **FALHA** (b);
- módulo sem classes de produção → `check` passa;
- classe de produção com teste cobrindo ≥80% → passa;
- código gerado em `build/generated` sem cobertura + código manual coberto → passa (a) (sem a exclusão a cobertura ficaria abaixo de 80%);
- código manual abaixo de 80% → falha (a regra de 80% segue valendo).

O `./gradlew build` da raiz executa esses testes (o build raiz depende do `check` do `build-logic`).

## Casos de teste

`contracts` (JUnit, Jackson 3 `tools.jackson.databind`):
- `Dado o schema de venda.canonica.registrada / Quando buildo contracts / Então a classe gerada existe e é serializável` (round-trip: objeto → JSON → objeto igual; JSON de fixture → objeto → JSON equivalente).
- Objeto vazio serializa como `{}` (campos nulos não saem). O leitor tolerante (campo desconhecido ignorado, ADR-0001 §4) é testado em `core-api`/`driver-bling`, com o mapper do Boot; o `JsonMapper` cru do Jackson 3 falha em campo desconhecido, e o gerado não valida `required` (validação de schema na borda é pós-MVP, ADR-0001).
- `$ref` dos YAML apontam para schemas existentes e os YAML não definem `properties` próprios.
- Os schemas não contêm `uniqueItems` nem `additionalProperties` nem referência a ERP específico ("bling" só como valor de exemplo/ enum não é usado no schema).

`core-api` e `driver-bling`: teste `@SpringBootTest` que injeta o `ObjectMapper` **do Boot 4** e faz round-trip do tipo gerado (prova compilar e rodar contra o `contracts` com o mapper configurado pelo Boot).

## Critérios de aceite

1. `./gradlew :contracts:build` gera as classes a partir do schema.
2. `core-api` e `driver-bling` compilam e testam contra os tipos gerados.
3. Nenhum DTO canônico escrito à mão em `contracts` (só `package-info.java` e testes).
4. Gate do JaCoCo conforme acima, provado por teste.
5. `./gradlew build` verde na raiz.

## Fora de escopo (NÃO FAZER)

POJOs escritos à mão; lógica no `contracts`; campos específicos de Bling; dois geradores; redefinir formato em AsyncAPI/OpenAPI; modelo completo/outros eventos/SemVer/tag (REC-010); validação de schema e fixtures no CI (REC-003); ArchUnit (REC-004).

## Evidências (2026-11-09, JDK 21 do SDKMAN)

- `./gradlew spotlessApply build --rerun-tasks`: **BUILD SUCCESSFUL** (Spotless, SpotBugs, testes, JaCoCo, `verificarDadosDeCobertura` em todos os módulos; o `check` da raiz executa o `build-logic:check`).
- `build-logic` `JacocoGateTest` (TestKit): 5 testes, 0 falhas (sem teste → FALHA; sem classes → passa; com teste → passa; gerado sem cobertura fica fora da base; classe manual sem cobertura derruba o gate).
- `contracts`: relatório JaCoCo sem nenhuma classe (o gerado está excluído e não há código manual); testes de round-trip (Jackson 3) e de estrutura passam.
- `core-api` e `driver-bling`: `ContratoCanonicoRoundTripTest` (ObjectMapper do Boot 4) passa.

## Pendências / observações

- Validação dos schemas e fixtures no CI (ADR-0001 item 3): REC-003.
- **Para o REC-010:** fixture compartilhada do evento (`java-test-fixtures`, hoje duplicada nos testes de `contracts`, `core-api` e `driver-bling`); `pattern` SemVer em `versao_contrato` e `$id` em URN; excluir o código gerado do SpotBugs.
- **Para o REC-003:** declarar a exclusão do código gerado como input rastreado do gate (compatibilidade com configuration cache; hoje o `.java` gerado é varrido em tempo de execução) e avaliar o custo do TestKit (~2 min) dentro do `check`.
- O `jsonschema2pojo-core` no `build-logic` é necessário (sem ele o script de convenção não compila: `GenerationConfig` é supertipo de `JsonSchemaExtension`); `setFileExtensions` ficou só com `.schema.json`, e o nome `VendaCanonica` se manteve. Modelo completo, `comandos/`, SemVer/tag: REC-010. ArchUnit (core não importa driver): REC-004.
- `core-api`/`driver-bling` só usam o tipo gerado em teste (não há código de produção que o consuma ainda).
- O SpotBugs analisa também as classes geradas (hoje sem achado de alta confiança); se atrapalhar no REC-010, excluir `build/generated` dele.
- Sem commit (regra do usuário): tudo na árvore da `feature/REC-002-contracts-codegen`.
