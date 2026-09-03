package org.nasa.telemetria.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operação de telemetria.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar "a telemetria não pôde ser gravada" de qualquer
 * outra falha de banco. É a distinção que decide o que fazer: <b>esta exceção nunca deve
 * derrubar a operação medida</b>, e quem a captura sabe disso pelo tipo, não por comentário.</p>
 *
 * <p><b>INVARIANTE.</b> Nunca carrega o conteúdo medido, só a operação e o alvo — o mesmo
 * cuidado do {@code Registro}: mensagem de erro é lida e copiada em canal que ninguém
 * controla.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É o próprio comportamento em caso de falha:
 * carrega {@link CausaRaiz} para o registrador único formatar uma vez.</p>
 */
public class FalhaNaTelemetriaException extends ErroDePipeline {

    public FalhaNaTelemetriaException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "a telemetria nao pode ser gravada ou lida — a operacao medida NAO foi afetada",
              causa);
    }
}
