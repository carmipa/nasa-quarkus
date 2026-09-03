package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco responde, mas o esquema não foi aplicado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar "o banco está fora" de "o banco está vazio". As
 * duas fazem a aplicação não funcionar e mandam investigar lugares opostos: a primeira é
 * infraestrutura, a segunda é o arranque que não migrou.</p>
 *
 * <p><b>Um banco vazio ACEITA conexão e responde {@code SELECT 1}.</b> É por isso que a
 * checagem de saúde conta a tabela de controle em vez de fazer a consulta trivial — e é por
 * isso que esta exceção existe: sem ela, "não migrou" viraria "banco fora" e alguém iria
 * conferir rede e firewall.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É o próprio: carrega
 * {@link CausaRaiz#CONFLITO_DE_ESTADO} — o estado do banco não é o esperado.</p>
 */
public class EsquemaNaoAplicadoException extends ErroDePipeline {

    public EsquemaNaoAplicadoException(String alvo) {
        super("verificar-saude", alvo, CausaRaiz.CONFLITO_DE_ESTADO,
              "o banco responde mas o esquema nao foi aplicado — a migracao rodou?");
    }
}
