# Recorra

Plataforma horizontal de inteligência de recompra. O sistema lê as vendas do ERP do comércio, aprende o ciclo de recompra de cada cliente e age **antes** do produto acabar, via WhatsApp.

A arquitetura é **Ports & Adapters, driver-based**: o `core-api` é agnóstico de ERP, e cada ERP tem um *driver* que traduz os dados para o **contrato canônico**.

> Contexto completo: [`tecDoc/CONTEXTO-PROJETO.md`](tecDoc/CONTEXTO-PROJETO.md) · Roadmap: [`tecDoc/roadmap-desenvolvimento-sdd.md`](tecDoc/roadmap-desenvolvimento-sdd.md) · Decisões: [`docs/adr/`](docs/adr/)

## Módulos

| Módulo | Tipo | Pacote | Papel |
|---|---|---|---|
| `contracts` | lib | `com.recorra.contracts` | Contrato canônico: JSON Schema → Java gerado ([ADR-0001](docs/adr/0001-formato-contrato-canonico.md)) |
| `common` | lib | `com.recorra.common` | Utilidades técnicas, regras ArchUnit |
| `core-api` | **app** :8080 | `com.recorra.core` | Domínio, inteligência de recompra, ações |
| `drivers/driver-sdk` | lib | `com.recorra.driver.sdk` | Kit para escrever drivers (resiliência, publicação, idempotência) |
| `drivers/driver-bling` | **app** :8081 | `com.recorra.driver.bling` | Driver do Bling (somente leitura no ERP) |
| `messaging/wa-cloud` | lib | `com.recorra.messaging.wacloud` | Adapter WhatsApp oficial (Meta Cloud API) |

Regra de ouro: **o core nunca conhece um ERP**. A única ponte entre core e drivers é o `contracts`.

As convenções de build ficam em `build-logic/` (convention plugins) e as versões em `gradle/libs.versions.toml`.

## Stack

Java 21 · Spring Boot 4.1 ([ADR-0002](docs/adr/0002-baseline-spring-boot-4.md)) · Gradle 9 (Kotlin DSL) · springdoc 3 · Spotless (google-java-format) · SpotBugs · JaCoCo

## Como rodar

Pré-requisito: um JDK qualquer para iniciar o Gradle. O JDK 21 do build é baixado sozinho pelo toolchain, se você não tiver.

```bash
./gradlew build                      # compila, formata (check), SpotBugs, testes e cobertura
./gradlew :core-api:bootRun          # sobe só o core em http://localhost:8080
./gradlew :drivers:driver-bling:bootRun   # sobe só o driver em http://localhost:8081

curl localhost:8080/actuator/health  # {"status":"UP"}
open http://localhost:8080/swagger-ui.html
```

Se o `spotlessCheck` acusar formatação, rode `./gradlew spotlessApply`.

A porta pode ser trocada com a variável `PORT`.

## Fluxo de trabalho (SDD)

Cada card do roadmap segue `spec.md` → `plan.md` → código → **um PR verde**, na branch `feature/REC-XXX-descricao` → `develop`. Respeite a seção **NÃO FAZER** de cada card. A Definition of Done está no roadmap (§3.3).
