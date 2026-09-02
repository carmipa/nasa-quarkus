package org.nasa.persistencia.domain.ports;

import org.nasa.persistencia.domain.Migracao;

import java.util.Map;

/**
 * O que o banco já sabe sobre as migrações — e como registrar mais uma.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a memória do esquema. Sem ela, toda subida
 * reaplicaria todo o DDL, e a segunda execução falharia com "tabela já existe".</p>
 *
 * <p><b>INVARIANTES.</b></p>
 * <ol>
 *   <li>{@link #aplicarERegistrar} é <b>atômico</b>: ou o DDL e o registro entram juntos,
 *       ou nenhum dos dois. Registrar sem aplicar deixaria o banco uma versão à frente do
 *       próprio esquema; aplicar sem registrar faria a próxima subida repetir o DDL.</li>
 *   <li>{@link #checksumsAplicados} devolve versão → checksum, que é o par que permite
 *       detectar migração editada.</li>
 * </ol>
 *
 * <p><b>FALHA.</b> Qualquer erro do banco vira exceção específica com causa-raiz. Não há
 * caminho de degradação: sem registro confiável, não há como saber o que já rodou.</p>
 */
public interface RegistroDeMigracoesPort {

    /** Cria a tabela de controle se ainda não existir. Idempotente. */
    void prepararControle();

    /** Versão → checksum do que já foi aplicado neste banco. */
    Map<Integer, String> checksumsAplicados();

    /** Aplica o DDL e registra a versão, na MESMA transação. */
    void aplicarERegistrar(Migracao migracao);
}
