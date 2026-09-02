package org.nasa.core.telemetria;

import org.nasa.core.erro.CausaRaiz;

/**
 * O contrato mínimo para uma fatia contar <b>por que</b> algo falhou.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a peça que permite ao kernel registrar log e
 * telemetria de qualquer falha <b>sem conhecer nenhuma fatia</b>. O kernel declara a
 * interface; a telemetria de cada fatia a implementa. A seta continua apontando
 * fatia → kernel, e mesmo assim a contagem causal acontece.</p>
 *
 * <p><b>INVARIANTE.</b> Contar é barato e não pode falhar: uma implementação que lance
 * daqui transformaria "falhei ao geocodificar" em "falhei ao contar que falhei ao
 * geocodificar", e a segunda mensagem esconde a primeira. Implementações absorvem os
 * próprios erros e registram em log.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Nenhum: o método não devolve nada e não
 * declara exceção verificada. Falha da telemetria nunca derruba a operação medida —
 * medição é apoio, não função.</p>
 */
@FunctionalInterface
public interface ContadorDeCausaRaiz {

    /** Soma uma ocorrência à causa. Nunca lança. */
    void contar(CausaRaiz causa);
}
