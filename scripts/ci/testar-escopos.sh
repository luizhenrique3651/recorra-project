#!/usr/bin/env bash
# Prova localmente os filtros de path do CI (cenário do REC-003: "PR que altera só o driver-bling →
# só o job do driver-bling"). Uso: scripts/ci/testar-escopos.sh   (sai com 1 se algum caso falhar)
set -uo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"
falhas=0

caso() { # <descrição> <esperado: "todos core bling"> <arquivos...>
  local descricao=$1 esperado=$2; shift 2
  local obtido
  obtido=$(printf '%s\n' "$@" | "$dir/escopos.sh" | sed 's/^[a-z]*=//' | tr '\n' ' ' | sed 's/ $//')
  if [ "$obtido" = "$esperado" ]; then
    echo "ok   - $descricao"
  else
    echo "FALHA - $descricao (esperado '$esperado', obtido '$obtido')"; falhas=$((falhas + 1))
  fi
}

caso "só driver-bling → só o driver"        "false false true"  drivers/driver-bling/src/main/Foo.java
caso "driver-sdk → tudo (ninguém depende)"  "true false false"    drivers/driver-sdk/build.gradle.kts
caso "messaging → tudo (ninguém depende)"   "true false false"    messaging/wa-cloud/build.gradle.kts
caso "só core-api → só o core"              "false true false"  core-api/src/main/resources/application.yml
caso "contracts → tudo"                     "true false false"    contracts/src/main/schemas/modelo/x.schema.json
caso "contracts + driver → tudo"            "true false false"    drivers/driver-bling/a.java contracts/b.json
caso "build-logic → tudo"                   "true false false"    build-logic/src/main/kotlin/x.gradle.kts
caso "catálogo de versões → tudo"           "true false false"    gradle/libs.versions.toml
caso "workflow alterado → tudo"             "true false false"    .github/workflows/ci.yml
caso "só docs/specs/md → nada"              "false false false" docs/adr/0005.md specs/REC-003/spec.md README.md
caso "caminho desconhecido → tudo"          "true false false"    algo-novo/arquivo.txt
caso "core + driver → os dois, sem 'todos'" "false true true"   core-api/a.java drivers/driver-bling/b.java
caso "diff vazio → nada"                    "false false false"

obtido=$("$dir/escopos.sh" --tudo </dev/null | sed 's/^[a-z]*=//' | tr '\n' ' ' | sed 's/ $//')
if [ "$obtido" = "true false false" ]; then echo "ok   - --tudo (push) → tudo"; else echo "FALHA - --tudo ($obtido)"; falhas=$((falhas + 1)); fi

# Regra do job final `ci-ok` (required check único): skipped só vale nos jobs de build.
resultado() { # <descrição> <esperado: ok|falha> <nome=resultado...>
  local descricao=$1 esperado=$2; shift 2
  if "$dir/resultado-final.sh" "$@" >/dev/null 2>&1; then obtido=ok; else obtido=falha; fi
  if [ "$obtido" = "$esperado" ]; then echo "ok   - ci-ok: $descricao"
  else echo "FALHA - ci-ok: $descricao (esperado $esperado, obtido $obtido)"; falhas=$((falhas + 1)); fi
}
resultado "tudo success"                         ok    escopos=success todos=success core-api=skipped driver-bling=skipped
resultado "builds skipped (só docs)"            ok    escopos=success todos=skipped core-api=skipped driver-bling=skipped
resultado "escopos falhou (builds skipped)"     falha escopos=failure todos=skipped core-api=skipped driver-bling=skipped
resultado "escopos skipped"                     falha escopos=skipped todos=skipped core-api=skipped driver-bling=skipped
resultado "escopos cancelado"                   falha escopos=cancelled todos=skipped core-api=skipped driver-bling=skipped
resultado "build do driver falhou"              falha escopos=success todos=skipped core-api=skipped driver-bling=failure
resultado "build cancelado"                     falha escopos=success todos=cancelled core-api=skipped driver-bling=skipped
resultado "job desconhecido ou sem resultado"   falha escopos=success todos=
resultado "argumento mal formado"               falha lixo

exit $((falhas > 0))
