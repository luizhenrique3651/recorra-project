# REC-004 · Base do `driver-sdk` + regras ArchUnit

Card: `tecDoc/roadmap-desenvolvimento-sdd.md` §REC-004 · EP-04 · 5 SP · branch `feature/REC-004-driver-sdk-archunit` (sobre o REC-003 não commitado). ADRs: 0001 (única ponte = contrato), 0002 (Boot 4 / Spring 7). Transporte do publisher: REC-043 (HTTP de ingestão idempotente), mas plugável.

## Contratos

Sem endpoint nem schema novo. API Java do `driver-sdk` (`com.recorra.driver.sdk`):

| Tipo | Papel |
|---|---|
| `ResilientErpClient` | GET resiliente (`Mono<T> get(Class<T>, uriTemplate, vars...)`) sobre o `WebClient` do driver. Camadas: retry → circuit breaker → rate limiter → timeout por chamada → chamada. |
| `ErpClientConfig` | Política (tentativas, backoff, `maxRetryAfter`, `callTimeout`, rate limiter, circuito); `padrao()` conservador (3 tentativas, 2 req/s, timeout 10 s, Retry-After até 60 s) e `withX` para ajustar. Valida na construção. |
| `CanonicalPublisher` | **Só a interface**: `PublishResult publish(Object evento, IdempotencyKey chave)`. Sem transporte; `PublishResult` = `ACCEPTED`/`DUPLICATE`. |
| `DelegatingCanonicalPublisher` | Base de decoradores = **ponto de extensão do `traceparent` (REC-005)**. Nenhuma propagação implementada. |
| `CanonicalMessage` | `(evento, chave)`; `toString` sem o evento (PII). |
| `IdempotencyKey` | `<comercio_id>:<erp>:<erp_venda_id>` via `paraVenda(comercioId, erp, erpVendaId)` (invariante §1.6 item 3: idempotência por comércio + ERP + id; o mesmo `erp_venda_id` em comércios diferentes não colide). `comercioId` e `erp` sem `:` (o id da venda pode, é o último campo). | |
| `IncrementalPoller` + `CursorStore` | Esqueleto: lê desde o cursor, publica, **só então** salva o cursor (at-least-once; a chave deduplica). Sem agendador, sem ERP. |
| `WebhookReceiver` | Esqueleto: assinatura → traduzir → publicar; assinatura inválida = `REJECTED` sem traduzir nem publicar. |

`evento` é `Object` porque os tipos gerados do `contracts` não têm supertipo comum (jsonschema2pojo, ADR-0001) e o contrato completo é o REC-010; quando houver um marcador/supertipo gerado, estreitar a assinatura.

## Decisões

- **Retry: Resilience4j, não `@Retryable` do Spring 7.** O retry precisa ler o `Retry-After` da exceção HTTP, compor com circuit breaker e rate limiter e rodar sobre `Mono` sem proxy de método. Versão: **Resilience4j 2.4.0, só os módulos core + `resilience4j-reactor`** (não usa o starter Spring Boot do Resilience4j, atrelado ao Boot 3): nenhum conflito com Boot 4/Spring 7 (compilou e rodou nos testes). Versão em `libs.versions.toml`.
- **Rate limiter por dentro do retry:** cada nova tentativa paga uma permissão, então o backoff após 429 nunca fura o limite do ERP. Espera pela permissão é reativa (timeout configurável).
- **Timeout por chamada** (`callTimeout`, `.timeout()` dentro do `Mono.defer`): `TimeoutException` repete e conta para o circuit breaker; sem isso um ERP que pendura prenderia o driver.
- **429/5xx/timeout/I-O repetem; outros 4xx não.** Backoff exponencial, ou o `Retry-After` (segundos **ou data HTTP**) quando maior; o `Retry-After` vale inteiro até `maxRetryAfter` (não é cortado no `maxBackoff`) e, **acima disso, não repete** (falha na hora). Circuit breaker conta 5xx e I/O; 4xx (inclusive 429) e o `RequestNotPermitted` do limiter são **ignorados** (`ignoreException`: nem sucesso nem falha), para não diluir a taxa de falha.
- **WebClient sem starter:** `spring-webflux` como `api` do SDK (versão do BOM), conector do JDK (`JdkClientHttpConnector`) por escolha do driver; o servidor dos apps (webmvc) não muda.
- **ArchUnit 1.5.1 num módulo próprio `architecture-tests`** (sem código de produção): é o único lugar que enxerga core, contrato, sdk e drivers juntos. Importa as classes compiladas de produção dos módulos (propriedades `recorra.classes.*`, rastreadas como input e com `dependsOn(classes)`).
- **Regras** (`RegrasDeArquitetura`): (1) `com.recorra.core..` não depende de `com.recorra.driver..`; (2) `com.recorra.driver..` não depende de `com.recorra.core..`; (3) `com.recorra.contracts..` não depende de core nem driver; (4) `com.recorra.driver.sdk..` não depende de outro `com.recorra.driver..` (nenhum ERP). A única ponte é `com.recorra.contracts`. Regra sem classe analisada **falha** (ArchUnit 1.x), então não há verde vazio.
- **Logs (SLF4J, sem PII):** só nome do cliente, tentativa/máximo, espera, status HTTP ou classe da exceção; **nunca** URI, query, headers, corpo nem `getMessage()` (a mensagem do WebClient traz a URI). WARN em retry, tentativas esgotadas, transição do circuit breaker, negativa do rate limiter, webhook com assinatura inválida (classe do receptor) e falha de publicação no poller (chave + posição, relança); DEBUG em Retry-After ilegível e fim de rodada. O ERROR único fica na borda (REC-005). `LogsDoSdkTest` (ListAppender) prova níveis e ausência de path/query/corpo.
- **`architecture-tests` descobre os módulos sozinho:** o Gradle injeta `recorra.classes.<módulo>` para todo subprojeto com plugin `java` (menos ele mesmo); o teste lê todas as propriedades com esse prefixo e falha se não vier nenhuma.
- `retry`/`reactor`/`slf4j` são `implementation` no SDK; `circuitbreaker`/`ratelimiter`/`webflux` ficam `api` (aparecem na API pública).
- `WebhookReceiver` é um esqueleto **síncrono provisório**: no REC-043 o endpoint responde 202 só depois de gravar no outbox e processa de forma assíncrona.
- `driver-bling` passou a depender de `driver-sdk` (critério "consumível"), provado por `DriverSdkConsumivelTest`.
- WireMock 3.13.2 `standalone` (dependências sombreadas, sem conflito com Jackson 3); a 4.x ainda é beta.

## CI

- `architecture-tests/**` cai no `todos` (caminho não mapeado). Os jobs por módulo (`core-api`, `driver-bling`) **agora também rodam `:architecture-tests:build`**: sem isso uma mudança só em `core-api/**` importando um driver passaria no CI sem ninguém avaliar as regras.
- `drivers/driver-sdk/**` e `messaging/**` continuam no `todos`: o `driver-bling` agora depende do SDK, mas o job do driver só roda os testes do driver (não os do SDK, nem a cobertura dele). Revisitar quando houver `driver-sdk` com job próprio.

## Casos de teste

| Cenário | Teste |
|---|---|
| Regra falha o build se core importar driver / driver importar core / contrato / sdk→ERP | `RegrasQuebramOBuildTest` (4 violadoras de fixture → `AssertionError`; caso legítimo só pelo contrato passa) |
| Regras passam no código real | `RegrasDeArquiteturaTest` (4) |
| Em 429, backoff e respeita o rate limiter | `ResilientErpClientTest`: backoff de 150 ms; `Retry-After: 1`; 2ª tentativa após 429 espera a renovação do limiter (1/600 ms); 3 chamadas com 2/600 ms; 429 esgota tentativas |
| Demais comportamentos | 404 não repete nem conta; 5xx abre o circuito e a chamada seguinte nem sai; 429 misturado a 5xx abre pelo limiar dos 5xx; **timeout** (WireMock com delay) repete e abre o circuito; **Retry-After** maior que o backoff vale inteiro, acima do teto falha sem repetir, aceita data HTTP; I/O repete |
| Idempotência, publisher, poller, webhook, config, logs | `IdempotencyKeyTest` (mesmo `erp_venda_id` em comércios diferentes → chaves diferentes), `LogsDoSdkTest`, `PublicacaoCanonicaTest` (decorador, sem PII no `toString`), `EsqueletosDeDriverTest` (cursor só avança após publicar), `ErpClientConfigTest` |
| `driver-sdk` consumível | `DriverSdkConsumivelTest` (driver-bling) |

Cobertura do `driver-sdk`: **99,5 %** de linha (202/203), gate de 80 % valendo.

Mutação manual: com uma classe em `core-api` importando `IdempotencyKey` (e o módulo dependendo do SDK), `:architecture-tests:test` ficou vermelho em `coreNaoImportaDriver`; revertida.

## Como verificar

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/current PATH=$HOME/.sdkman/candidates/java/current/bin:$PATH
./gradlew spotlessApply build
./gradlew :architecture-tests:test :drivers:driver-sdk:test
scripts/ci/testar-escopos.sh
```

## Fora de escopo (NÃO FAZER)

Regra de negócio no SDK; acoplar a ERP; acoplar o publisher a um transporte; propagar `traceparent` (REC-005); implementar o transporte HTTP de ingestão (REC-043); agendador do poller; ERP real (REC-0xx do driver).

## Pendências

- Tipo/supertipo comum dos eventos gerados (REC-010) para tirar o `Object` do `CanonicalPublisher`.
- Testes de timing do `ResilientErpClientTest` usam só limites inferiores, mas somam ~3 s (`Retry-After: 1` e janelas de 600 ms); reduzir se incomodar.
- Ações do GitHub por tag major; workflow só verificável num PR real (como no REC-003).
