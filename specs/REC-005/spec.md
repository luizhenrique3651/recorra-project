# REC-005 · Logging, rastreabilidade e mascaramento de PII

Card: `tecDoc/roadmap-desenvolvimento-sdd.md` §REC-005 · EP-01 · 5 SP · branch `feature/REC-005-logging-pii` (sobre REC-003 e REC-004 não commitados). ADRs: 0004 (mascarar também a mestra, as DEKs e os keysets AEAD/MAC), 0002 (Boot 4 / RestTestClient).

## Contratos

Sem endpoint de negócio. Efeitos visíveis: logs em JSON, header `traceparent` em toda resposta HTTP, erros em RFC 7807 com `traceId`.

| Peça (módulo `common`, salvo indicação) | Papel |
|---|---|
| `trace.Traceparent` | W3C `traceparent` (`00-<32hex>-<16hex>-<2hex>`): `parse`, `novo`, `filho`, `restaurar` (header completo **ou** só o `traceId`, como na coluna do outbox), `toHeader`. |
| `trace.TraceContext` / `MdcEscopo` | Contexto no MDC (`traceId`, `spanId`, `comercioId`); escopo `AutoCloseable` que **restaura** o valor anterior (aninhamento seguro) e limpa em `finally`. |
| `trace.ContextoDeTrabalho` | MDC por unidade de trabalho: `executar(comercioId, ...)` (job: trace **novo**) e `executar(comercioId, traceparentOuTraceId, ...)` (consumer/webhook/outbox: trace **restaurado**; valor ausente/inválido → trace novo, o log nunca fica sem `traceId`). Usado pelo REC-011. |
| `web.TraceparentFilter` | Entrada HTTP: mantém o `traceId` do header válido (novo span) ou cria um; devolve `traceparent` na resposta (inclusive de erro); limpa o MDC em `finally`. |
| `web.RequestLogFilter` | **Uma** linha INFO por request: método, **rota** (padrão `/clientes/{id}`, nunca o path real), query mascarada, status, duração. Sem body, sem headers; `/actuator` fora. |
| `web.ProblemaGlobalHandler` | RFC 7807 + `traceId`. `RejeicaoDeNegocioException` (4xx, sem stacktrace) → WARN só com `codigo` e status (nunca o detalhe) ; erros do Spring MVC (400/404/405...) → WARN sem stacktrace; qualquer outra exceção → **um** ERROR com stacktrace e 500 genérico ("Erro interno", sem detalhe interno). |
| `web.TraceparentRestClientInterceptor` | Saída com `RestClient`/`RestTemplate`: header do trace atual (novo span). Só para serviços nossos. |
| `logging.MascaradorDePii` + `MascaramentoJsonCustomizer` | Mascaramento **no appender**: aplicado a todo valor de texto do JSON (mensagem, stacktrace, MDC). `mascararQuery` decodifica nome e valor (URL) e mascara por chave: `?nome=Maria%20Silva&telefone=...&email=a%40b.com` e `co%64e=` saem `***`. |
| `driver-sdk`: `TracingCanonicalPublisher` | Decorador do `CanonicalPublisher` (ponto de extensão do REC-004): cada publicação roda num novo span do trace corrente (ou num trace novo); o transporte plugável lê `TraceContext.atual()`. Sem transporte no SDK. |
| `driver-sdk`: `TraceparentExchangeFilter` | Saída `WebClient` para o core (REC-043). **Não** usar no `ResilientErpClient`: o trace interno não vai ao ERP. |
| `observabilidade-common.yml` (resource do `common`) | `logging.structured.format.console=logstash`, `logging.structured.json.customizer=...MascaramentoJsonCustomizer`, `server.tomcat.accesslog.enabled=false`. Importado por core e driver (`spring.config.import`). |
| `ObservabilidadeAutoConfiguration` | Liga filtros e handler em todo app servlet com o `common` no classpath. |
| `testFixtures`: `CapturaDeLogJson` | Teste de appender: acopla ao root o **mesmo** `StructuredLogEncoder` do console (com o mascaramento) e devolve o JSON escrito. |

## Decisões

- **JSON pelo logging estruturado do Boot (formato `logstash`), sem dependência nova.** MDC sai no topo do JSON (`traceId`, `spanId`, `comercioId`). Mascaramento via `StructuredLoggingJsonMembersCustomizer` (um `ValueProcessor` de `String`), não por conversor de padrão: cobre mensagem, stacktrace e MDC de uma vez.
- **Mascaramento em duas frentes** (`MascaradorDePii`): por **chave** (`nome=`, `"access_token":`, `Authorization: Bearer`, `password`, `hmac`, `signature`, `master_key`, `dek`, `kek`, `keyset`, `encryptedKeyset`, `telefone`, `cpf`, `email`...) e por **formato** (e-mail, CPF/CNPJ, telefone BR, JWT, Bearer/Basic, bloco `keyData` de keyset Tink, blocos base64/hex de 40+ caracteres = mestra/DEKs/keysets). `code`/`state`/`token` só com `=` (não pegar "status code: 404").
- **Limites assumidos (documentados no Javadoc):** nome de pessoa só é pego com chave (texto livre não dá); números soltos de 11/14 dígitos e blocos de 40+ base64/hex são mascarados mesmo se inocentes (falso positivo > vazamento). A regra de ouro segue sendo **não logar o dado**; o mascaramento é a última barreira.
- **Query OAuth:** `RequestLogFilter` usa `mascararQuery` (parâmetros sensíveis → `***`, demais passam pelo mascarador) e o access log do Tomcat fica desligado (o pattern padrão traria a query); além disso o mascaramento do appender cobre qualquer log que carregue `code=`/`state=`/`token=`.
- **Níveis:** ERROR com stacktrace uma única vez, na borda (`ProblemaGlobalHandler`); WARN sem stacktrace para rejeição de negócio e de requisição; body nunca é logado (o filtro nem o lê).
- **Desvio do card (decorador do `CanonicalPublisher`):** o card pede a infra "em `common`", mas o decorador (`TracingCanonicalPublisher`) ficou no `driver-sdk`, porque o `CanonicalPublisher` mora no SDK e o `common` não pode importar o SDK (o SDK depende do `common`, nunca o contrário). O `common` entrega o que o decorador usa: `TraceContext`, `Traceparent`, `MdcEscopo`.
- **Logs e MDC no Reactor (sem Hook global):** o `ResilientErpClient` captura o contexto do chamador na inscrição do `get()` (`TraceContext.capturar`) e o restaura, via `MdcMono` (operador próprio que abre `MdcEscopo` na inscrição e em cada sinal), na tentativa e no trecho retry/CB/limiter; assim as novas tentativas (resubscritas em thread do timer) e os WARN de retry/CB/limiter carregam `traceId`/`comercioId` do chamador, e o `TraceparentExchangeFilter` lê o mesmo snapshot do Reactor Context. O snapshot não vai ao ERP.
- **Exceção fora do MVC:** o `TraceparentFilter` (mais externo) captura `ServletException`/`RuntimeException` dentro do escopo do MDC, loga **um** ERROR com stacktrace e `traceId`, responde 500 `ProblemDetail` genérico (se não commitada) e não relança. O `RequestLogFilter` loga `status=500` e só a **classe** da exceção quando a cadeia lança.
- **Config de log sem import manual:** `ObservabilidadeEnvironmentPostProcessor` (`META-INF/spring.factories`) adiciona `observabilidade-common.yml` como property source de baixa precedência: todo app com o `common` sobe com JSON + mascaramento (um app novo não loga PII em claro por esquecimento). Core e driver verificam isso no `Environment`.
- `Traceparent` gera ids com `ThreadLocalRandom` + `HexFormat` (ids de trace não são segredo; `SecureRandom` sofre contenção/pinning com virtual threads).
- **"Toda linha traz `traceId`"** vale para as linhas dentro de request ou de unidade de trabalho; linhas de startup e de threads fora de contexto não têm trace, e isso é esperado.
- **Mascaramento sem corromper a correlação:** `traceId`/`spanId`/`traceFlags` ficam FORA do `ValueProcessor` (por nome do campo); CPF/CNPJ/telefone exigem fronteira alfanumérica (`(?<!\\w)`/`(?!\\w)`: não casam dentro de hex/identificador); o bloco longo base64/hex não casa caminho de classe/recurso (tem `/` e quase nenhum dígito); DEK/Tink/chaves seguem mascarados.
- **Um único ERROR também no Reactor:** `ReactorErroDescartado` (instalado pela `ObservabilidadeAutoConfiguration` quando o Reactor está no app): erro descartado por cancelamento (ex.: "Request cancelled" do conector JDK após timeout) = DEBUG sem stack; outro descartado = WARN só com a classe.
- **Ruído de teste:** `gradle/logback-test.xml` (root INFO) nos módulos sem Spring (`architecture-tests`, `driver-sdk`, `contracts`); módulos com Spring Boot não o usam, porque um `logback-test.xml` substituiria a configuração do Boot (JSON + mascaramento).
- **`common` com Spring/Logback `compileOnly`:** a lib não impõe nada; os apps trazem os dois. As classes puras (Traceparent, MDC, mascarador) funcionam sem Spring. O `driver-sdk` agora depende do `common` (permitido pelas regras ArchUnit; o `common` não conhece core nem driver).
- **Outbox (REC-042) ainda não existe:** entrega só o utilitário de restaurar o trace (`Traceparent.restaurar` / `ContextoDeTrabalho.restaurar`), que aceita o `traceId` puro da futura coluna.
- **Débito do REC-004:** `recorra.java-conventions` passou a ter `logback-classic` como `testRuntimeOnly` (versão do BOM, no catálogo), eliminando "SLF4J: No SLF4J providers were found" em `architecture-tests` e `contracts`.
- Os apps passaram a depender de `:common` pela convenção `recorra.spring-boot-app-conventions`.

## Casos de teste

| Cenário do card | Teste |
|---|---|
| Telefone, nome, CPF, e-mail e token → mascarados no appender | `MascaradorDePiiTest` (todos os tipos, mestra/DEKs/keysets Tink, JWT, não-mascara texto inocente); `ObservabilidadeWebTest.appenderMascaraPii`; `ObservabilidadeCoreTest` e `ObservabilidadeDriverTest` (appender de cada app) |
| `traceparent` na entrada → mesmo `traceId` no ProblemDetail e no log | `ObservabilidadeWebTest.traceparentDeEntradaChegaAoProblemDetailEAosLogs`, `semHeaderGeraTrace`, `headerMalformadoComecaTraceNovo` |
| Rejeição de negócio = WARN sem stacktrace; inesperada = um único ERROR com stacktrace | `rejeicaoDeNegocioEWarnSemStacktrace`, `inesperadoEUmUnicoErrorComStacktrace` (500 genérico, sem "jdbc"/exceção), `erroDeRequisicaoDoSpringEWarn` |
| Body nunca em log | `bodyNuncaApareceNoLog` |
| Job com comércios A e B → só o seu `comercioId`; MDC vazio ao final | `ContextoDeTrabalhoTest.jobPorComercioNaoVaza`, `limpaEmExcecao`, `escoposAninhadosRestauram` |
| Consumer com traceparent do header/outbox → traceId restaurado, limpo ao final | `ContextoDeTrabalhoTest.consumerRestauraOTraceId` (header completo e `traceId` puro), `valorInvalidoComecaTraceNovo` |
| `GET /callback?code=abc&state=xyz` → mascarados | `ObservabilidadeWebTest.queryOauthMascarada` (+ `RequestLogFilter` usa a rota-padrão: `rotaSaiPeloPadrao`) |
| Ponto de extensão do REC-004 | `TracingSdkTest` (decorador: mesmo trace/novo span/restaura, inclusive em exceção; filtro de saída envia o header) |

## Como verificar

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/current PATH=$HOME/.sdkman/candidates/java/current/bin:$PATH
./gradlew spotlessApply build
./gradlew :common:test            # mascaramento, MDC, filtros, handler
```

Manual: `./gradlew :drivers:driver-bling:bootRun` e `curl -i -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' localhost:8081/nao/existe` → ProblemDetail com `traceId` e a linha de log JSON com o mesmo.

## Fora de escopo (NÃO FAZER)

Logar body; stacktrace em WARN ou repetido por camada; adiar o mascaramento; stack de tracing/Grafana (só headers e MDC); PII "só em dev"; coluna do outbox (REC-042); resolução do tenant que preenche `comercioId` nas requests (REC-011).

## Pendências

- `comercioId` só aparece nos logs de job/consumer/webhook (via `ContextoDeTrabalho`); nas requests HTTP quem o preenche é a resolução de tenant (REC-011), que deve abrir o escopo.
- O log de request sai depois da resposta: em testes de integração, aguardar (Awaitility) a linha.
- Logs de bibliotecas de terceiros também passam pelo mascarador (é no appender), mas o mascarador não conhece formatos de segredo de cada lib; revisar quando entrar o OAuth do Bling e o Tink (REC-016).
- Texto livre com nome de pessoa sem chave não é detectável; o REC-072 deve verificar os pontos de log do domínio.
- **Spring Security (REC-012):** o `@ExceptionHandler(Exception.class)` do `ProblemaGlobalHandler` vai transformar `AccessDeniedException`/`AuthenticationException` em 500; ao entrar o Security, tratá-las antes (401/403 em WARN sem stacktrace) ou deixá-las subir ao `AccessDeniedHandler`/`AuthenticationEntryPoint`.
- **MDC é `ThreadLocal`:** não propaga para `@Async`, executores nem virtual threads filhas; o job/consumer deve abrir o `ContextoDeTrabalho` **dentro da thread que executa** o trabalho (não na que o agenda).
- Telefone fixo de 10 dígitos **sem separador** não é mascarado solto (colide com epoch em segundos); só com chave, `+55`, (DDD) ou separadores. Chaves terminadas em `name`/`nome` (exceto técnicas como `logger_name`, `hostname`) e `token <valor>` sem separador são mascaradas.
- As flags do `traceparent` (ex.: `00` = não amostrado) são preservadas de ponta a ponta (chave `traceFlags` no MDC, que também sai no JSON).
