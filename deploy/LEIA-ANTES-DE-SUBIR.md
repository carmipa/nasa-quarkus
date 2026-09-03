# Subir na VPS — `desastres.carminati.dev.br`

**Estado em 03/09/2026, medido de fora:**

| endereço | resultado |
|---|---|
| DNS `desastres.carminati.dev.br` | resolve para `187.77.249.75` ✅ |
| `http://desastres.carminati.dev.br/` | **200**, e é a página `"Default Site"` do openresty |
| `https://desastres.carminati.dev.br/` | **não responde** (`http=000`) — não há TLS escutando |
| `/saude` | **404** — a aplicação não está lá |

O DNS está pronto. **A aplicação não está no ar**, e o que atende é um
*placeholder*.

`187.77.249.75` é a VPS `srv1522378`: o `~/.ssh/config` do Paulo declara
`Host vps-paulo hostinger 187.77.249.75`, usuário `root`. Ela hospeda **outros
nove serviços**, e é por isso que cada passo abaixo é reversível e nenhum toca
configuração compartilhada.

---

## Passo 0 — MEDIR, antes de mudar qualquer coisa

O ponto que decide o resto: **existe um painel gerenciando o openresty?**
CyberPanel e o stack da Hostinger geram os vhosts, e um arquivo escrito à mão é
**sobrescrito** na próxima alteração pelo painel — o site cai sem ninguém ter
mexido nele.

```bash
ssh vps-paulo 'hostname; whoami'
ssh vps-paulo 'nginx -V 2>&1 | head -1'
ssh vps-paulo 'ls -la /usr/local/lsws/conf/vhosts/ 2>/dev/null'     # CyberPanel/OLS
ssh vps-paulo 'ls -la /usr/local/openresty/nginx/conf/conf.d/ 2>/dev/null'
ssh vps-paulo 'ls -la /etc/nginx/sites-enabled/ 2>/dev/null'
ssh vps-paulo 'nginx -T 2>/dev/null | grep server_name'
ssh vps-paulo 'ss -tlnp | head -30'
ssh vps-paulo 'systemctl list-units --type=service --state=running | head -30'
ssh vps-paulo 'df -h /; free -m'
ssh vps-paulo 'java -version 2>&1; ls /usr/lib/jvm/ 2>/dev/null'
```

**Havendo painel**, o vhost se cria pelo painel e só o bloco `location /` do
[`desastres.carminati.dev.br.conf`](desastres.carminati.dev.br.conf) é colado no
campo de configuração personalizada. **Sem painel**, aquele arquivo vale inteiro.

**Java 25 é requisito** e o último comando diz se ele está lá. Se não estiver,
instalar é um `apt` — que mexe no sistema compartilhado, e portanto é decisão do
Paulo, não minha.

---

## Passo 1 — o usuário e as pastas

```bash
ssh vps-paulo 'useradd --system --no-create-home --shell /usr/sbin/nologin nasa'
ssh vps-paulo 'mkdir -p /opt/nasa-quarkus /var/lib/nasa-quarkus'
ssh vps-paulo 'chown -R nasa:nasa /var/lib/nasa-quarkus'
```

**Por que um usuário próprio.** A aplicação não precisa de nada fora da pasta
dela. Rodando como `root`, um defeito de travessia de caminho alcançaria os
outros nove serviços — e este projeto tem, no `DocumentacaoCatalogo`, uma trava
de travessia justamente porque essa família de defeito é fácil de reintroduzir.

`/var/lib/nasa-quarkus` é separado de `/opt` de propósito: o código é
substituído a cada versão, o **banco não**. Misturados, um `rm -rf` da pasta de
código apagaria os dados.

---

## Passo 2 — o artefato

O `quarkus-run.jar` é *fast-jar*: ele **precisa** da pasta `lib/` ao lado, e
copiar só o `.jar` produz `ClassNotFoundException` no arranque.

Duas formas. **Construir aqui e enviar** é a preferível — a VPS não precisa de
Gradle nem de rede para o Maven Central, e o que sobe é exatamente o que foi
testado:

```bash
# na máquina local
./gradlew clean quarkusBuild
scp -r build/quarkus-app vps-paulo:/opt/nasa-quarkus/
ssh vps-paulo 'chown -R nasa:nasa /opt/nasa-quarkus'
```

**Ou clonar e construir lá**, que foi a intenção original ("vou clonar lá").
Custa Gradle, JDK completo e ~1 GB de dependências na VPS:

```bash
ssh vps-paulo 'cd /opt && git clone https://github.com/carmipa/nasa-quarkus.git'
ssh vps-paulo 'cd /opt/nasa-quarkus && ./gradlew quarkusBuild'
```

> O repositório é **público**, então o clone não pede credencial. E `gs/` não
> está versionado — há duas camadas impedindo isso, e a guarda de caminhos
> proibidos roda a cada commit.

---

## Passo 3 — o serviço

```bash
scp deploy/nasa-quarkus.service vps-paulo:/etc/systemd/system/
ssh vps-paulo 'systemctl daemon-reload && systemctl enable --now nasa-quarkus'
ssh vps-paulo 'systemctl status nasa-quarkus --no-pager'
ssh vps-paulo 'journalctl -u nasa-quarkus -n 50 --no-pager'
```

**Conferir ANTES de mexer no proxy** — a aplicação escuta só em `127.0.0.1`,
então o teste é de dentro:

```bash
ssh vps-paulo 'curl -s -o /dev/null -w "saude=%{http_code}\n" http://127.0.0.1:8080/saude'
ssh vps-paulo 'curl -s http://127.0.0.1:8080/saude'
```

`/saude` conta a tabela `esquema_migracao` — não faz `SELECT 1`. Um banco vazio
aceita conexão e responde `SELECT 1`; é por isso que a checagem conta a tabela
de controle, e é o que distingue *"o banco está fora"* de *"a migração não
rodou"*.

Se o arranque cair, as três causas prováveis, em ordem:

| sintoma no journal | causa |
|---|---|
| `FusoHorarioNaoUtcException` | falta `-Duser.timezone=UTC` — a catraca derruba de propósito |
| `NASA_DB_PATH` não resolvido | a variável não chegou; ela **não tem padrão** em produção |
| `attempt to write a readonly database` | `ReadWritePaths` não cobre a pasta do banco. O WAL cria `-wal` e `-shm` **na mesma pasta** |

---

## Passo 4 — o certificado, e só depois o vhost

**Nesta ordem.** Instalar o vhost antes de o certificado existir impede o nginx
de recarregar — e um `reload` que falha deixa a configuração **antiga** no ar,
o que é sorte, não plano.

```bash
ssh vps-paulo 'certbot certonly --webroot -w /var/www/html -d desastres.carminati.dev.br'
ssh vps-paulo 'ls -la /etc/letsencrypt/live/desastres.carminati.dev.br/'
```

O Cloudflare está em **DNS only** — ele só resolve o nome e **não termina TLS**.
O certificado tem de sair desta máquina; foi isso que a medição da porta 443 sem
resposta mostrou.

Só então:

```bash
scp deploy/desastres.carminati.dev.br.conf vps-paulo:/etc/nginx/sites-available/
ssh vps-paulo 'ln -s /etc/nginx/sites-available/desastres.carminati.dev.br.conf /etc/nginx/sites-enabled/'
ssh vps-paulo 'nginx -t'        # NUNCA recarregar sem isto passar
ssh vps-paulo 'systemctl reload nginx'
```

`nginx -t` é o portão: ele valida a sintaxe **sem** aplicar. Recarregar com
configuração inválida é o caminho mais curto para derrubar os outros nove
serviços junto — e eles não têm nada a ver com este deploy.

---

## Passo 5 — provar que subiu

```bash
curl -s -o /dev/null -w "http=%{http_code}\n"  http://desastres.carminati.dev.br/
curl -s -o /dev/null -w "https=%{http_code}\n" https://desastres.carminati.dev.br/
curl -s https://desastres.carminati.dev.br/saude
curl -s https://desastres.carminati.dev.br/sitemap.xml | head -4
curl -s -o /dev/null -w "telemetria=%{http_code}\n" https://desastres.carminati.dev.br/telemetria
```

O esperado: `80` redirecionando para `443`, `https=200`, `/saude` respondendo,
o sitemap com o domínio real (não `localhost`) e `/telemetria` em **404**.

**E a prova que importa, porque um deploy não pode derrubar vizinho** — os
outros nove serviços continuam de pé:

```bash
ssh vps-paulo 'systemctl list-units --type=service --state=failed --no-pager'
```

A saída tem de vir **vazia**. Comparar com o que o Passo 0 registrou.

---

## O que reverter, se der errado

Nesta ordem, e cada passo é independente:

```bash
ssh vps-paulo 'rm /etc/nginx/sites-enabled/desastres.carminati.dev.br.conf'
ssh vps-paulo 'nginx -t && systemctl reload nginx'
ssh vps-paulo 'systemctl disable --now nasa-quarkus'
```

Nada disso toca os outros nove serviços, e o banco em `/var/lib/nasa-quarkus`
sobrevive — de propósito.

---

## O que ainda não existe

- **Login do GitHub.** A telemetria está barrada no proxy com `404`, que é uma
  tapa-buraco honesta: ela impede o acesso e não pretende ser autorização.
  A chave OAuth é do Paulo.
- **Renovação automática do certificado.** O `certbot` instala um *timer*
  próprio, e o Passo 4 depende dele. Vale conferir com
  `systemctl list-timers | grep certbot` — noventa dias passam rápido, e a
  falha aparece com o site inacessível.
