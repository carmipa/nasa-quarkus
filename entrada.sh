#!/bin/sh
# =============================================================================
# Entrada do contêiner — existe por causa do §9 da planta: UM ARQUIVO DE LOG POR
# EXECUÇÃO.
#
# O carimbo da execução é gerado no LANÇAMENTO (`build.gradle` faz isso no host).
# Dentro do contêiner não há Gradle: sem este script, `nasa.log.execucao` cairia
# no padrão `manual` e todo restart escreveria no MESMO arquivo, com rotação
# desligada de propósito. Log de execuções misturadas responde errado — foi
# exatamente essa a cicatriz que o §9 registra.
# =============================================================================
set -eu

: "${NASA_DB_PATH:?NASA_DB_PATH nao definida — o perfil prod nao tem padrao para o banco, e subir apontando para lugar nenhum e pior que nao subir}"

CARIMBO="${NASA_LOG_EXECUCAO:-$(date -u +%Y%m%d-%H%M%S)}"
PASTA_LOG="${NASA_LOG_PASTA:-/dados/logs/execucoes}"

# O handler de arquivo do JBoss LogManager não cria a árvore de diretórios: sem
# isto a aplicação sobe e simplesmente não grava log, em silêncio.
mkdir -p "${PASTA_LOG}" "$(dirname "${NASA_DB_PATH}")"

export QUARKUS_LOG_FILE_PATH="${PASTA_LOG}/nasa-${CARIMBO}.log"
export NASA_LOG_PASTA="${PASTA_LOG}"
export JAVA_OPTS_APPEND="${JAVA_OPTS_APPEND:-} -Dnasa.log.execucao=${CARIMBO}"

exec /opt/jboss/container/java/run/run-java.sh "$@"
