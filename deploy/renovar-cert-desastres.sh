#!/bin/bash
# Renova o certificado de desastres.carminati.dev.br e recarrega o proxy.
#
# POR QUE ESTE SCRIPT PRECISA EXISTIR. O certificado foi emitido por certbot
# DENTRO do container do NPM, fora da interface dele — publicar pela interface
# exigiria a senha do admin, e escrever direto no banco do NPM arriscaria os
# outros tres sites de producao que ele serve. O preco dessa escolha e este
# arquivo: a renovacao automatica do NPM so cobre os hosts que estao no banco
# dele, entao sem este cron o certificado venceria em 2026-12-02 e o dominio
# cairia com erro de TLS, calado.
#
# Mesmo padrao — e mesmo motivo — de `renovar-cert-aspm.sh` e
# `renovar-cert-carminati-fst.sh`, que ja existiam nesta maquina.
#
# `set -euo pipefail`: se a renovacao falhar, o script PARA e nao recarrega o
# nginx. Recarregar depois de uma falha nao conserta nada e apaga o unico
# sinal de que algo deu errado — o log fica com "reload ok" no fim.
set -euo pipefail

docker exec infra-proxy-app-1 certbot renew --cert-name desastres --quiet \
    --webroot --webroot-path=/data/letsencrypt-acme-challenge

# `nginx -t` ANTES do reload, sempre. Um reload com configuracao invalida
# deixaria a configuracao antiga no ar — o que e sorte, nao plano — e nesta
# maquina derrubaria junto frameworknet, challengepride e binmapper.
docker exec infra-proxy-app-1 nginx -t
docker exec infra-proxy-app-1 nginx -s reload

echo "$(date -Is) renovacao de desastres.carminati.dev.br concluida"
