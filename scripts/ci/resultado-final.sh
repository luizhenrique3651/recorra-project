#!/usr/bin/env bash
# Regra do job final `ci-ok` do workflow (REC-003): o ÚNICO required check do GitHub.
# Motivo: job SKIPPED conta como sucesso; se `escopos` falhasse, todos os jobs de build ficariam
# skipped e o PR passaria sem teste nenhum.
#
#   scripts/ci/resultado-final.sh escopos=<r> todos=<r> core-api=<r> driver-bling=<r>
#
# <r> é o `needs.<job>.result` (success|failure|cancelled|skipped). Sai com 1 se:
#   - `escopos` não for success (nem skipped é aceito nele);
#   - qualquer job de build for failure ou cancelled (skipped é aceito nos jobs de build);
#   - algum resultado vier vazio/desconhecido ou o argumento for mal formado.
set -uo pipefail

[ "$#" -gt 0 ] || { echo "uso: $0 job=resultado..." >&2; exit 1; }
falhou=0
for par in "$@"; do
  case "$par" in *=*) ;; *) echo "argumento mal formado: $par" >&2; exit 1 ;; esac
  job=${par%%=*} resultado=${par#*=}
  case "$job:$resultado" in
    escopos:success) ;;
    escopos:*) echo "FALHA: escopos=$resultado (precisa ser success)" >&2; falhou=1 ;;
    *:success | *:skipped) ;;
    *) echo "FALHA: $job=$resultado" >&2; falhou=1 ;;
  esac
done
exit $falhou
