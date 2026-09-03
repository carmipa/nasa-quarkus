# O deploy — como ele realmente é

**No ar desde 03/09/2026:** <https://desastres.carminati.dev.br>

> **Este documento já esteve errado, e vale saber por quê.** A primeira versão
> descrevia systemd + openresty, porque eu medi o cabeçalho `Server: openresty`
> de fora e concluí o produto a partir do motor. A imagem
> `jc21/nginx-proxy-manager` **é construída sobre openresty** — o cabeçalho dizia
> a verdade sobre o binário e mentia sobre a ferramenta. Cabeçalho de servidor
> identifica o motor, não o produto; para saber o produto, é preciso olhar de
> dentro.

## A forma

```
internet
   │  80/443
   ▼
infra-proxy-app-1  (Nginx Proxy Manager, container)
   │  rede docker `nginx-proxy-network`
   ▼
nasa-desastres:8080  (container, imagem nasa-quarkus:latest)
   │
   ▼
/opt/nasa-quarkus/dados/nasa.db   (volume ./dados:/dados)
```

A porta é publicada como `127.0.0.1:18083:8080`. **É esse `127.0.0.1` que
protege a aplicação** — ele é o laço da VPS, e só quem está na máquina ou na
rede docker alcança. Não é firewall, é topologia: não há regra para alguém
desfazer sem querer.

> Não ponha `quarkus.http.host=127.0.0.1` na aplicação achando que ajuda. Dentro
> do container esse é o laço do **próprio container**, e o `docker-proxy` deixa
> de alcançar a aplicação — o site não sobe. Eu escrevi essa chave e o
> Dockerfile teve de sobrepô-la; ela foi removida, com o motivo no
> `application.properties`.

## Publicar uma versão nova

```bash
ssh vps-paulo
cd /opt/nasa-quarkus
git pull --ff-only
docker compose build
docker compose up -d
docker inspect nasa-desastres --format '{{.State.Health.Status}}'   # espere `healthy`
```

E então provar, de fora — **200 não prova tela certa neste projeto**, o Qute
imprime expressão inválida como texto sem falhar:

```bash
B=https://desastres.carminati.dev.br
curl -s "$B/saude"
for r in / /desastres /desastres/mapa /alertas /contato /documentacao /sitemap.xml; do
  printf "%-24s %s\n" "$r" "$(curl -s -o /dev/null -w '%{http_code}' "$B$r")"
done
curl -s "$B/" | grep -oE '\{#[a-z]+|\{cdi:|\.raw\}'    # tem de vir VAZIO
```

**E os vizinhos** — a VPS tem outros nove serviços, e um deploy que derruba
vizinho é um deploy que falhou:

```bash
for d in frameworknet challengepride binmapper; do
  printf "%-16s %s\n" "$d" "$(curl -s -o /dev/null -w '%{http_code}' "https://$d.carminati.dev.br/")"
done
ssh vps-paulo 'docker ps --filter health=unhealthy --format "{{.Names}}"'   # tem de vir VAZIO
```

## O proxy, e por que ele é um arquivo à mão

[`npm-900-desastres.conf`](npm-900-desastres.conf) →
`/opt/infra-proxy/data/nginx/proxy_host/900.conf`

Publicar pela interface do NPM exige a senha do admin. O banco dele
(`/opt/infra-proxy/data/database.sqlite`) serve **outros três sites de
produção**, e escrever nele direto arriscaria os três para ganhar um. Um arquivo
novo em `proxy_host/` entra pelo mesmo `include` dos gerados, não toca o banco, e
sai com um `rm`.

O número **900** é folga deliberada: o NPM gera `1.conf`, `2.conf`, `3.conf`,
`100.conf` a partir dos ids do banco dele, e sobrescreveria um número baixo sem
avisar.

Depois de qualquer alteração:

```bash
ssh vps-paulo 'docker exec infra-proxy-app-1 nginx -t'        # o portão
ssh vps-paulo 'docker exec infra-proxy-app-1 nginx -s reload'
```

`nginx -t` **antes** do reload, sempre. Recarregar com configuração inválida
deixa a antiga no ar — o que é sorte, não plano.

## O certificado, e o que ele custa

Emitido por certbot dentro do container do NPM, **fora da interface dele**.
Consequência que precisa ficar dita: **a renovação automática do NPM não cobre
este certificado**, porque ele não está no banco. Sem cron, vence em
**2026-12-02** e o domínio cai com erro de TLS, calado.

Por isso:

| | |
|---|---|
| script | `/opt/infra-proxy/scripts/renovar-cert-desastres.sh` |
| cron | `/etc/cron.d/renovar-cert-desastres` — seg e qui, 4h47 |
| provado em | 03/09/2026, `certbot renew --dry-run` → *"all simulated renewals succeeded"* |

O horário é deslocado dos outros dois scripts (`aspm` 4h37, `carminati-fst`
4h17) para não renovar três certificados no mesmo minuto e bater no limite de
taxa do Let's Encrypt.

**Um cron que ninguém testou é uma promessa.** O `--dry-run` é o que o torna
evidência — refaça-o se mexer no caminho do webroot.

## O login do GitHub — desligado, e como acender

**Nasce desligado** (`%prod.quarkus.oidc.enabled=${OIDC_LIGADO:false}`), porque
o OAuth App ainda não existe. Sem isso o Quarkus recusaria o arranque por falta
de `client-id`, e o site inteiro ficaria fora do ar esperando uma credencial que
protege **uma tela**.

Para acender, no `/opt/nasa-quarkus/.env` (permissão `600`, fora do git):

```bash
OIDC_LIGADO=true
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
```

O callback do OAuth App é `https://desastres.carminati.dev.br/login/github/retorno`.
Depois, `docker compose up -d` — nada de código muda.

**E aí o `location /telemetria { return 404; }` do `900.conf` sai**, porque a
tela passa a ter fechadura de verdade. Enquanto ele está lá, a telemetria se vê
por túnel:

```bash
ssh -L 18083:127.0.0.1:18083 vps-paulo    # e abra http://127.0.0.1:18083/telemetria
```

## O que fica fechado, e o que não

Só `/telemetria`. Todo o resto é público, de propósito: é uma vitrine.

A configuração é **lista de negação** e não de permissão. A primeira versão era
o contrário e punha `/desastres`, `/alertas`, `/saude` e `/sitemap.xml` atrás de
login — quebrando o healthcheck do container e escondendo a função principal do
site. Listar o que é público obriga a lembrar de cada rota nova, e a esquecida
fica fechada sem ninguém entender por quê.

**O risco desta escolha, declarado:** rota sensível nova nasce **pública**. É
aceitável porque não há dado pessoal no sistema — não há cadastro, e o alerta
não grava nada. No dia em que houver, esta decisão precisa ser revista.

## Reverter

Cada passo é independente e nenhum toca os outros nove serviços:

```bash
ssh vps-paulo 'cd /opt/nasa-quarkus && git checkout <commit-anterior> && docker compose up -d --build'
ssh vps-paulo 'rm /opt/infra-proxy/data/nginx/proxy_host/900.conf'   # tira o domínio do ar
ssh vps-paulo 'docker exec infra-proxy-app-1 nginx -t && docker exec infra-proxy-app-1 nginx -s reload'
```

O banco vive em `/opt/nasa-quarkus/dados/` e sobrevive a tudo isso — de
propósito. Ele guarda 21 mil eventos sincronizados da NASA e **nenhum dado
pessoal**.
