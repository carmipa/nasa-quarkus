package org.nasa.core.tempo;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

/**
 * Implementação de produção do {@link Relogio}: o relógio da JVM, em UTC.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a única classe do sistema autorizada a perguntar as
 * horas ao mundo. Toda outra classe recebe o {@link Relogio} injetado — e é por isso que
 * um teste consegue congelar o tempo sem gambiarra.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> {@link Instant#now()} é UTC por definição, e o tipo
 * de retorno não permite devolver outra coisa. Nenhum {@code LocalDateTime} atravessa
 * esta fronteira.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não há caminho de falha próprio. Se o
 * relógio do host estiver errado, o sistema inteiro está errado junto — por isso a data
 * dos eventos naturais vem da API da NASA e não daqui.</p>
 */
@ApplicationScoped
public class RelogioSistema implements Relogio {

    @Override
    public Instant agora() {
        return Instant.now();
    }
}
