/*
 * SCRIPT DO FORMULÁRIO DE ENDEREÇO — e só dele.
 *
 * PROPÓSITO: copiar, para os campos, o que o CEP trouxe — e SOMENTE nos campos
 *   que estão VAZIOS.
 *
 * A REGRA DO "NÃO SOBRESCREVER" É O CORAÇÃO DESTE ARQUIVO. Quem corrigiu o nome
 *   da rua sabe algo que a base do CEP ainda não sabe: normalmente a rua mudou
 *   de nome e a base não atualizou. Um preenchimento automático que apaga a
 *   correção da pessoa é pior que nenhum — ela digita de novo, o campo apaga de
 *   novo, e ela conclui que a tela está brigando com ela.
 *
 * POR QUE A COPIA É FEITA AQUI, e não pelo servidor devolvendo os campos
 *   prontos: trocar os campos pelo HTMX apagaria o NÚMERO e o COMPLEMENTO já
 *   digitados — justamente os dois que o CEP não sabe. O fragmento traz os
 *   valores em atributos `data-`, e este script decide o que aproveitar.
 *
 * FALHA: sem este arquivo, o resultado da consulta continua VISÍVEL na tela
 *   (o fragmento é HTML de verdade, não dado cru), e os campos são preenchidos
 *   à mão. Perde-se a digitação poupada, não a informação.
 */
(function () {
  'use strict';

  var caixa = document.getElementById('resultado-cep');
  if (!caixa) {
    return;
  }

  var mapa = {
    logradouro: 'logradouro',
    bairro: 'bairro',
    localidade: 'localidade',
    uf: 'uf'
  };

  document.body.addEventListener('htmx:afterSwap', function (evento) {
    if (evento.target !== caixa) {
      return;
    }
    var achado = caixa.querySelector('.cep-achado');
    if (!achado) {
      return;
    }
    Object.keys(mapa).forEach(function (chave) {
      var campo = document.getElementById(mapa[chave]);
      var valor = achado.dataset[chave];
      // SO se estiver vazio. Esta condicao e a regra inteira.
      if (campo && valor && campo.value.trim() === '') {
        campo.value = valor;
      }
    });
  });
})();
