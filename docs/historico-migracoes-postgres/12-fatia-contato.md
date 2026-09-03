# Fatia `contato`

Por onde a pessoa é avisada. **O defeito característico desta fatia não produz erro:
produz silêncio na hora do desastre.**

## As duas invariantes

**INV-CONTATO-001 — o e-mail identifica UM contato.** O projeto original expunha
`GET /api/contatos/email/{email}` devolvendo *um* contato, sem que nada garantisse existir
apenas um. Esse endpoint era ambíguo por construção: com dois contatos no mesmo e-mail,
qual dos dois ele devolvia?

**INV-CONTATO-002 — o tipo é um conjunto fechado**, garantido pela restrição
`contato_tipo_conhecido` da migração V002.

### Por que o tipo fechado importa

No original, `tipoContato` era um `<input type="text">` livre, com "Principal" preenchido
por padrão. Texto livre num campo de classificação produz, em pouco tempo, `Principal`,
`principal`, `PRINCIPAL` e `Pincipal` na mesma coluna — todos parecendo certos numa tela
que mostra um contato de cada vez.

A consequência é concreta: a fatia de alerta pergunta *"quais são os contatos de
EMERGÊNCIA deste cliente?"*. Um contato gravado como `emergencia` sem acento simplesmente
**não apareceria** — e o silêncio seria idêntico ao de "esta pessoa não tem contato de
emergência".

## Só `EMERGENCIA` recebe alerta

E o padrão de quem não escolhe é `PRINCIPAL`, o mais conservador. Promover alguém a
contato de emergência por engano faz uma pessoa receber aviso que não pediu — e, pior, faz
parecer que a cobertura existe.

Por isso a tela **diz a consequência antes de salvar**, ao lado do campo, e a lista marca
visivelmente quem recebe. Não pode depender de alguém ler o rótulo do tipo com atenção.

## E-mail obrigatório, telefones não

O e-mail é o único canal que o sistema sabe usar. Aceitar um contato só com telefone
criaria um registro que **parece completo** e por onde nada será enviado.

O e-mail é normalizado para minúsculas: sem isso, `Ana@X.com` e `ana@x.com` viram dois
contatos, o `UNIQUE` não enxerga a duplicata, e o alerta sai **duas vezes** para a mesma
pessoa.

## Telefone guarda só dígitos

Mesma cicatriz do documento do cliente: guardar como digitado faz o mesmo número existir
em quatro formas, e nenhuma busca encontra as outras três.

### Um achado de boa-fé que mudou o código

`(11) 3456-7890` no campo *telefone* dá 10 dígitos, e era recusado com *"esperado 8 ou 9
dígitos, recebi 10"*. Tecnicamente correto e inútil — manda contar dígitos.

É o erro que **qualquer pessoa comete**, porque é assim que se escreve telefone no Brasil.
A mensagem agora diz: *"parece o número com o DDD junto; o DDD tem campo próprio ao lado"*.

Descoberto porque o meu próprio fixture de teste caiu nele.

## O vínculo com o cliente

Um contato de emergência **solto** nunca recebe aviso: a varredura parte dos endereços do
cliente e caminha até os contatos dele. Sem a ligação, não há caminho. O formulário de
cadastro diz isso, e permite ligar na mesma operação.
