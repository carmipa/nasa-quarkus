package org.nasa.persistencia.presentation.bootstrap;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.erro.RegistradorDeFalha;
import org.nasa.persistencia.application.AplicadorDeMigracoes;

/**
 * Leva o banco ao dia no arranque, antes de qualquer requisição ser atendida.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Ninguém precisa rodar SQL à mão para o sistema
 * funcionar — nem quem desenvolve, nem quem for corrigir o trabalho. Subiu, o esquema
 * está no lugar.</p>
 *
 * <p><b>INVARIANTE.</b> Roda no {@link StartupEvent}, ou seja, <b>antes</b> de a
 * aplicação aceitar tráfego. Migrar com requisição em andamento significaria consulta
 * atravessando DDL, e no SQLite isso é disputa de escrita no mesmo arquivo.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> <b>Falha fechada: a exceção sobe e o boot
 * cai.</b> É a escolha certa e é deliberada — a alternativa seria subir com esquema
 * incompleto, e aí a primeira consulta falha longe da causa, possivelmente depois de já
 * ter gravado dado errado. A falha é registrada com causa-raiz antes de subir, para que o
 * log diga <b>o quê</b> e <b>por quê</b>, e não só o rastro de pilha.</p>
 */
@ApplicationScoped
public class MigracaoStartup {

    private static final Logger LOG = Logger.getLogger(MigracaoStartup.class);

    @Inject
    AplicadorDeMigracoes aplicador;

    void aoIniciar(@Observes StartupEvent evento) {
        try {
            aplicador.executar();
        } catch (ErroDePipeline falha) {
            // Registrar ANTES de deixar subir: quem lê o log precisa ver a causa-raiz,
            // não apenas o rastro de pilha do container derrubando o boot.
            RegistradorDeFalha.registrar(falha);
            throw falha;
        }
    }
}
