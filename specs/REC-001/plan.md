# REC-001 · Plano

1. Remover `messaging:wa-terceiro`: `include` no `settings.gradle.kts`, diretório `messaging/wa-terceiro/`, linha do README e comentário em `recorra.library-conventions`. Marcar o item 3 da ADR-0003 como `[x]`.
2. `./gradlew clean build --rerun-tasks` com JDK 21 (SDKMAN): confirmar Spotless, SpotBugs, testes e JaCoCo.
3. `bootRun` de `core-api` (8080) e `driver-bling` (8081), `curl /actuator/health`, parar os apps.
4. Corrigir o que a verificação apontar, sem tocar no escopo do REC-003/005.
5. Reportar evidências.
