package org.nasa.evento.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Um campo do evento nao descreve um evento utilizavel.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Recusa no domínio o que não serve para nada: evento sem
 * {@code eonetId} não pode ser deduplicado — a próxima sincronização o inseriria de novo,
 * e a estatística e o mapa cresceriam com cópias. Evento sem título não tem o que mostrar
 * na tela nem no aviso que chega à pessoa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O alvo carrega o nome do campo. Numa sincronização de
 * centenas de eventos, saber QUAL campo faltou é o que separa "a NASA mudou o contrato"
 * de "um evento veio torto".</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}.
 * Na sincronização NÃO derruba o lote: o evento torto é contado e pulado, e os demais
 * entram. Perder a sincronização inteira por causa de um evento seria trocar um problema
 * pequeno por um apagão de dados.</p>
 */
public class EventoInvalidoException extends ErroDePipeline {

    public EventoInvalidoException(String campo, String motivo) {
        super("validar-evento", campo, CausaRaiz.DADO_INVALIDO, "campo " + campo + ": " + motivo);
    }
}
