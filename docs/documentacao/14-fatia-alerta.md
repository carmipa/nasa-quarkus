# Fatia `alerta`

A saída de todo o sistema. Cadastro, endereço, contato, sincronização e geodésia existem
para produzir uma linha aqui e entregá-la.

## Padrão outbox: registrar antes de enviar

O aviso é gravado como `PENDENTE` **antes** de qualquer envio.

A ordem inversa — enviar e depois gravar — perde o registro se o processo cair entre as
duas coisas: a pessoa recebeu o aviso e o sistema não sabe, então avisa de novo na próxima
varredura. Gravar primeiro troca *"avisar duas vezes"* por *"no pior caso, avisar com
atraso"*, que é a troca certa.

## INV-ALERTA-001

**O mesmo evento não avisa o mesmo cliente duas vezes.** A chave é
`(cliente_id, evento_id)`, e mora no **banco**. Uma tempestade que dura cinco dias aparece
em cinco varreduras; sem a chave, seriam cinco mensagens.

### Prova

```
varredura 1:  candidatos=2  dentro=2  novos=2  jaExistiam=0
varredura 2:  candidatos=2  dentro=2  novos=0  jaExistiam=2
```

## `DO NOTHING` aqui é o certo — e é o oposto do evento

Na sincronização de eventos, `DO NOTHING` seria defeito: a posição muda e precisa ser
atualizada. Aqui o aviso **já foi dado**, e regravá-lo significaria reenviar.

A mesma cláusula, decisões opostas — e as duas escritas no código, para que ninguém
"padronize" uma delas depois.

## Como esta fatia fala com as outras sem conhecê-las

Ela **não importa** nenhuma classe de `cliente`, `contato`, `endereco` ou `evento`. Tem o
próprio **modelo de leitura**, com SQL sobre o esquema compartilhado. A guarda de fronteira
passa com seis fatias, e a regra 3 é a prova.

## A lacuna, declarada em voz alta

**Não há servidor de e-mail.** O adaptador em uso registra no log e **não entrega a
ninguém**. Por isso:

- cada envio sai em `WARN`, não em `INFO` — para a linha se destacar;
- a API expõe `entregaDeVerdade: false`, com a ressalva por escrito;
- o despacho registra `MEIO_NAO_ENTREGA_DE_VERDADE` antes de processar a fila;
- a tela mostra a ressalva **no topo**, e cada linha "Enviado" carrega *"registrado, não
  entregue"* ao lado.

Um adaptador que fingisse sucesso silencioso seria **pior que não ter alerta nenhum**: a
tela mostraria cobertura que não existe, e a descoberta viria no dia do desastre.

## Nenhum aviso desaparece

Falhou vira `FALHOU` com a causa-raiz gravada — nunca some, nunca volta a `PENDENTE`
sozinho. Voltar a pendente automaticamente criaria tentativa infinita sobre um erro
permanente. E `tentativas` sempre incrementa: é o que separa "falhou uma vez" de "falha
sempre".

## O destino sai mascarado

`pa***@exemplo.com`, no log e na tela. A auditoria é justamente a tela que alguém abre
para mostrar a outra pessoa: dá para conferir para onde foi, não dá para colher endereços
de um print.
