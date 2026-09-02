/*
 * MÁSCARA DE CPF/CNPJ — componente compartilhado.
 *
 * PROPÓSITO DE NEGÓCIO: pontuar o documento enquanto se digita, para a pessoa
 *   conferir contra o papel que tem na mão. É a única forma em que um CPF é
 *   legível por um humano.
 *
 * ONDE ESTÁ A LINHA (a mesma do CSS): é COMPONENTE porque duas telas usam —
 *   cadastrar e alterar — e amanhã contato usa. Duplicar a função em duas
 *   pastas garantiria que uma das duas divergisse na primeira correção.
 *
 * ISTO NÃO É VALIDAÇÃO, E A DISTINÇÃO É O PONTO:
 *   - formatar é ajudar a LER, e é trabalho do navegador;
 *   - validar é decidir se ACEITA, e é trabalho do servidor, sempre.
 *   O front legado validava CPF em JavaScript antes de enviar. Regra validada
 *   nos dois lados é regra que diverge — e a que vale é a do servidor. Aqui
 *   nada é recusado no navegador: o campo aceita o que for digitado, o servidor
 *   decide, e a tela mostra o que ele respondeu.
 *
 * INVARIANTE: só pontua. Nunca bloqueia tecla, nunca corta dígito além do
 *   limite de CNPJ, nunca impede o envio.
 *
 * FALHA: se este arquivo não carregar, o campo continua aceitando o documento
 *   sem pontuação — que é exatamente o que o servidor guarda. Perde-se conforto,
 *   não função.
 */
(function (janela) {
  'use strict';

  function pontuar(digitos) {
    if (digitos.length <= 11) {
      return digitos
        .replace(/^(\d{3})(\d)/, '$1.$2')
        .replace(/^(\d{3})\.(\d{3})(\d)/, '$1.$2.$3')
        .replace(/\.(\d{3})(\d{1,2})$/, '.$1-$2');
    }
    return digitos
      .replace(/^(\d{2})(\d)/, '$1.$2')
      .replace(/^(\d{2})\.(\d{3})(\d)/, '$1.$2.$3')
      .replace(/\.(\d{3})(\d)/, '.$1/$2')
      .replace(/(\d{4})(\d{1,2})$/, '$1-$2');
  }

  /** Liga a máscara a um campo. Seguro chamar com `null`. */
  janela.aplicarMascaraDeDocumento = function (campo) {
    if (!campo) {
      return;
    }
    campo.addEventListener('input', function () {
      // 14 dígitos é o CNPJ, o maior documento aceito. Cortar aqui evita que a
      // máscara produza pontuação sem sentido para o que passar disso — mas o
      // SERVIDOR é quem recusa o tamanho errado, não este corte.
      var digitos = campo.value.replace(/\D/g, '').slice(0, 14);
      campo.value = pontuar(digitos);
    });
  };
})(window);
