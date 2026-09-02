# Como rodar

## Desenvolvimento

```
gradlew quarkusDev
```

Sobe em `http://localhost:8080` e **abre o navegador sozinho**. O Dev Services levanta um
contêiner PostgreSQL 17 automaticamente — não é preciso configurar banco, usuário ou senha.

**Exige Docker.** É o preço da troca de SQLite por PostgreSQL, e é permanente.

Para desligar a abertura automática do navegador:

```
%dev.nasa.dev.abrir-navegador=false
```

## Testes

```
gradlew test
```

Também sobe um contêiner. O reaproveitamento está **desligado de propósito**: com ele, o
banco sobreviveria entre execuções e os testes de esquema — que usam chaves fixas —
passariam na primeira rodada e falhariam na segunda, esbarrando na duplicata que eles
mesmos deixaram.

## Produção

```
NASA_DB_URL=jdbc:postgresql://host:5432/nasa \
NASA_DB_USER=... \
NASA_DB_PASSWORD=... \
gradlew rodar
```

Ou, com o jar empacotado:

```
java -Duser.timezone=UTC -jar build/quarkus-app/quarkus-run.jar
```

**A flag de fuso não é opcional.** Sem ela o arranque cai, com a mensagem trazendo o
comando pronto — a `CatracaDeFusoUtc` recusa qualquer fuso que não seja offset zero e fixo.
`gradlew rodar` já passa a flag.

As três variáveis de ambiente **não têm valor padrão**. Faltando qualquer uma, o arranque
cai: subir com credencial adivinhada é pior que não subir.

## O que acontece no arranque

1. **Fuso conferido** — se não for UTC, para aqui;
2. **Banco verificado** — servidor no ar, credencial aceita, base existe. Cada falha tem
   mensagem própria com a correção;
3. **Migrações aplicadas** — checksums conferidos **inteiros** antes de aplicar qualquer
   uma;
4. **Faxina de log** — apaga o que passou de 30 dias ou do teto de contagem.

## Guardas antes de commitar

```
pwsh guardas/guardas.ps1
```

Roda a guarda de segredos e a de caminhos proibidos. O hook de `pre-commit` já as chama —
e **bloqueia o commit** se alguma reprovar. Três estados: `0` passou · `1` reprovou · `2`
**não verificou**, que não é aprovação.

## Sincronizar dados para ver o sistema funcionando

```
POST /api/eventos/sincronizar?limite=50&dias=30
POST /api/clientes          {"nome":"...","documento":"..."}
POST /api/enderecos         {"cep":"01310-200","clienteId":1}
POST /api/contatos          {"email":"...","tipoContato":"EMERGENCIA"}
POST /api/contatos/{id}/vincular/{clienteId}
POST /api/alertas/varrer?raioKm=100&dias=30
POST /api/alertas/despachar
```

Ou pelas telas, que fazem o mesmo caminho: **Desastres → Clientes → Endereços → Contatos →
Alertas**.
