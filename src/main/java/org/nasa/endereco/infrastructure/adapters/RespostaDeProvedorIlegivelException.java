package org.nasa.endereco.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O provedor externo respondeu, mas a resposta não pôde ser lida.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa três coisas que terminam iguais na tela e são
 * muito diferentes na causa: o CEP não existe (resposta legítima), o provedor está fora
 * (indisponibilidade) e o provedor <b>mudou o formato da resposta</b> (isto aqui). O
 * terceiro é o mais traiçoeiro: o serviço está no ar, responde 200, e o sistema para de
 * entender o que ele diz. Sem uma exceção própria, isso viraria "provedor indisponível" e
 * ninguém iria olhar o contrato.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada. Nunca devolver endereço parcialmente lido: meio
 * endereço é pior que nenhum, porque parece preenchido e vai para o banco.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#PROVEDOR_RECUSOU} — o caso de uso a trata
 * como falha daquele provedor e tenta o seguinte, exatamente como faria com uma queda.</p>
 */
public class RespostaDeProvedorIlegivelException extends ErroDePipeline {
    public RespostaDeProvedorIlegivelException(String provedor, String alvo, Throwable causaTecnica) {
        super("interpretar-resposta-" + provedor, alvo, CausaRaiz.PROVEDOR_RECUSOU,
              "o provedor respondeu, mas a resposta nao pode ser lida — o contrato pode ter mudado",
              causaTecnica);
    }
}
