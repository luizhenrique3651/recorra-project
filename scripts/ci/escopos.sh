#!/usr/bin/env bash
# Escopos de CI a partir dos arquivos alterados (REC-003). Lê um caminho por linha no stdin e
# imprime, uma por linha, as chaves que o workflow usa: todos=true|false, core=..., bling=...
#
#   git diff --name-only <base>...<head> | scripts/ci/escopos.sh
#
# Regras (a mesma função roda no GitHub e localmente; testada por scripts/ci/testar-escopos.sh):
#   - docs/specs/tecDoc/*.md (sem código): nenhum job;
#   - contracts/, build-logic/, common/, gradle/, raiz do build, scripts/ e .github/: roda TUDO;
#   - messaging/ e drivers/driver-sdk/: roda TUDO (o driver-bling depende do sdk, mas o job do driver
#     não roda os testes/cobertura dele; messaging/ ainda não tem job próprio);
#   - core-api/: só o core;
#   - drivers/driver-bling/: só o driver;
#   - qualquer outro caminho desconhecido: roda TUDO (falha segura).
#   --tudo: ignora o stdin e marca tudo (push em develop/main).
set -euo pipefail

todos=false core=false bling=false
lista() {
  if [ "${1:-}" = "--tudo" ]; then echo "(tudo)"; else cat; fi
}
while IFS= read -r arquivo; do
  [ -z "$arquivo" ] && continue
  case "$arquivo" in
    "(tudo)") todos=true ;;
    docs/* | specs/* | tecDoc/* | *.md | .gitignore | .env.example) ;;
    core-api/*) core=true ;;
    drivers/driver-bling/*) bling=true ;;
    *) todos=true ;;
  esac
done < <(lista "${1:-}")

# `todos` já cobre os módulos: com todos=true os flags por módulo saem false (o `if` dos jobs por
# módulo também exige todos != true), sem duplicar a mesma informação.
if [ "$todos" = true ]; then core=false bling=false; fi
echo "todos=$todos"
echo "core=$core"
echo "bling=$bling"
