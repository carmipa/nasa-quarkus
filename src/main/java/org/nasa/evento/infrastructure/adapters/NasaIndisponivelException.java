package org.nasa.evento.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A API da NASA nao respondeu.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "a NASA está fora" de "não há eventos". As duas
 * produzem uma lista vazia na tela e significam coisas opostas: na primeira os dados
 * antigos continuam válidos e é só esperar; na segunda o mundo está calmo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Falha AQUI não apaga nada. A base local continua com
 * o que já foi sincronizado, e o alerta continua funcionando sobre esses dados — a
 * sincronização é uma atualização, não a fonte de verdade em tempo real.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PROVEDOR_INDISPONIVEL}, 503 na borda — "tente de novo" é a reação
 * certa, e é diferente de "seu pedido está errado".</p>
 */
public class NasaIndisponivelException extends ErroDePipeline {

    public NasaIndisponivelException(String alvo, Throwable causa) {
        super("sincronizar-eonet", alvo, CausaRaiz.PROVEDOR_INDISPONIVEL,
              "a API da NASA nao respondeu; os eventos ja sincronizados continuam validos", causa);
    }
}
