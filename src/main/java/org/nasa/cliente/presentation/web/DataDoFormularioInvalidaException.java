package org.nasa.cliente.presentation.web;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A data digitada no formulário não é uma data.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O {@code <input type="date">} do navegador entrega
 * sempre {@code AAAA-MM-DD} — mas o formulário pode chegar de um navegador antigo que
 * renderiza o campo como texto livre, de alguém colando um valor, ou de uma requisição
 * montada à mão. Sem esta classe, o {@code DateTimeParseException} sobe cru e a pessoa vê
 * um erro 500 por ter digitado {@code 14/05/1990} em vez de {@code 1990-05-14}.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O campo {@code alvo} é {@code dataNascimento}, e não
 * o valor digitado — é o nome do campo que a tela usa para destacar onde está o erro. A
 * mensagem diz o <b>formato esperado</b>: mandar "data inválida" sem dizer qual forma se
 * espera obriga a pessoa a adivinhar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO},
 * que o mapeador de borda traduz para 400 na API. Na tela, o resource a captura e devolve
 * o mesmo formulário preenchido, com o aviso ao lado do campo.</p>
 */
public class DataDoFormularioInvalidaException extends ErroDePipeline {

    public DataDoFormularioInvalidaException(String valorRecebido, Throwable causaTecnica) {
        super("ler-formulario", "dataNascimento", CausaRaiz.DADO_INVALIDO,
              "data de nascimento invalida: esperado AAAA-MM-DD (ex.: 1990-05-14), recebi \""
              + (valorRecebido == null ? "" : valorRecebido) + "\"",
              causaTecnica);
    }
}
