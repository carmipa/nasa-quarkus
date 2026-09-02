package org.nasa.contato.domain.exceptions;

import org.nasa.contato.domain.TipoContato;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O tipo de contato informado nao existe.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Existe para que um valor inventado seja RECUSADO em vez
 * de virar mais uma variante gravada na coluna. O legado aceitava texto livre, e é assim
 * que "Principal" e "principal" acabam sendo duas classificações diferentes para a mesma
 * intenção — e é assim que um contato de emergência deixa de ser encontrado.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> A mensagem lista os valores aceitos. Dizer só "tipo
 * inválido" obriga quem topou com o erro a procurar no código quais são — e, na prática,
 * a pessoa tenta adivinhar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO},
 * que o mapeador de borda traduz para 400. Na tela, o formulário volta com o campo
 * apontado.</p>
 */
public class TipoDeContatoDesconhecidoException extends ErroDePipeline {

    public TipoDeContatoDesconhecidoException(String recebido) {
        super("validar-contato", "tipoContato", CausaRaiz.DADO_INVALIDO,
              "tipo de contato desconhecido: " + recebido + ". Aceitos: "
              + java.util.Arrays.toString(TipoContato.values()));
    }
}
