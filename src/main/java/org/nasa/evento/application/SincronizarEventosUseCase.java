package org.nasa.evento.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.ports.FonteDeEventosNaturaisPort;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Traz os eventos da NASA para a base local.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que mantém o alerta atualizado. Sem sincronizar, o
 * sistema decide sobre o mundo de ontem — e um evento natural muda de posição em horas.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Idempotente por {@code eonetId}, garantido no BANCO.</b> Rodar duas vezes
 *       seguidas não duplica nada. A garantia é o {@code ON CONFLICT} do repositório, não
 *       uma checagem daqui — que não sobreviveria a duas sincronizações simultâneas.</li>
 *   <li><b>Conta o que AGIU e o que se ABSTEVE, separadamente.</b> "Trouxe 50" e "os
 *       mesmos 50 de sempre" não podem produzir a mesma linha de log: a diferença entre
 *       elas é a diferença entre a NASA estar publicando e a sincronização estar parada.</li>
 *   <li><b>Falha da NASA NÃO apaga nada.</b> A base local continua válida e o alerta
 *       continua funcionando sobre ela — sincronizar é atualizar, não é a fonte de verdade
 *       em tempo real.</li>
 *   <li><b>Zero eventos é ALERTA, não sucesso.</b> A EONET praticamente nunca devolve
 *       vazio para uma janela de dias: zero quase sempre significa filtro errado ou
 *       contrato mudado. Sucesso silencioso aqui é o que faz ninguém perceber que o
 *       sistema parou de receber dados.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> NASA fora ⇒ exceção de indisponibilidade
 * (503), com a base intacta. Um evento torto é pulado pelo adaptador, contado, e os
 * demais entram.</p>
 */
@ApplicationScoped
public class SincronizarEventosUseCase {

    private static final Logger LOG = Logger.getLogger(SincronizarEventosUseCase.class);
    private static final String OPERACAO = "sincronizar-eonet";

    @Inject
    FonteDeEventosNaturaisPort fonte;

    @Inject
    RepositorioDeEventosPort repositorio;

    @Inject
    Relogio relogio;

    /**
     * O que a sincronizacao fez.
     *
     * @param trazidos    quantos a NASA devolveu
     * @param novos       quantos NAO existiam aqui  (AGIU)
     * @param atualizados quantos ja existiam        (ABSTEVE de criar)
     * @param duracao     quanto levou
     */
    public record Resultado(int trazidos, int novos, int atualizados, Duration duracao) {
    }

    /**
     * Traz o ANO inteiro — o arquivo histórico.
     *
     * <p><b>PROPÓSITO.</b> O sistema original mostrava desastres desde o começo dos
     * registros da EONET, divididos por ano. Isto é o que enche essa série.</p>
     *
     * <p><b>Por que um método próprio, e não {@code executar} com `dias` grande.</b>
     * Medido em 02/09/2026: sem recorte de data, a EONET devolve os mais recentes
     * primeiro — {@code limit=2000} traz 2000 eventos <b>todos de 2026</b> e nenhum de
     * 2015. Pedir "os últimos 4000 dias" não alcança o arquivo; só o recorte alcança.</p>
     *
     * <p><b>Zero aqui NÃO é o mesmo alerta que zero na janela recente.</b> Um ano antigo
     * pode legitimamente ter poucos eventos, e a EONET publicava menos em 2015 (342) do
     * que em 2025 (4612). O aviso existe, mas diz "ano vazio", que é uma pergunta — não
     * "sincronização quebrada", que seria uma afirmação sem prova.</p>
     *
     * @param ano    o ano civil, em UTC
     * @param limite teto por ano; 6000 cobre o maior ano medido com folga
     */
    public Resultado executarAno(int ano, int limite) {
        var inicio = relogio.agora();
        List<EventoNatural> daNasa = fonte.buscarDoAno(ano, limite);

        if (daNasa.isEmpty()) {
            LOG.warn(Registro.recusa(OPERACAO, String.valueOf(ano), "ANO_SEM_EVENTOS"));
        }

        int novos = 0;
        int atualizados = 0;
        for (EventoNatural evento : daNasa) {
            var r = repositorio.gravarOuAtualizar(evento);
            if (r.inserido()) {
                novos++;
            } else {
                atualizados++;
            }
        }

        var duracao = Duration.between(inicio, relogio.agora());
        LOG.info(Registro.de(OPERACAO, String.valueOf(ano), "trazidos=" + daNasa.size()
                + " novos=" + novos + " atualizados=" + atualizados, duracao));
        return new Resultado(daNasa.size(), novos, atualizados, duracao);
    }

    public Resultado executar(int limite, Integer dias, boolean apenasAtivos) {
        var inicio = relogio.agora();
        List<EventoNatural> daNasa = fonte.buscar(limite, dias, apenasAtivos, Optional.empty());

        if (daNasa.isEmpty()) {
            // Zero NAO e sucesso silencioso: a EONET praticamente nunca devolve vazio
            // para uma janela de dias. Quase sempre e filtro errado ou contrato mudado.
            LOG.warn(Registro.recusa(OPERACAO, "lote", "NENHUM_EVENTO_RETORNADO"));
        }

        int novos = 0;
        int atualizados = 0;
        for (EventoNatural evento : daNasa) {
            var r = repositorio.gravarOuAtualizar(evento);
            if (r.inserido()) {
                novos++;
            } else {
                atualizados++;
            }
        }

        var duracao = Duration.between(inicio, relogio.agora());
        LOG.info(Registro.de(OPERACAO, "lote", "trazidos=" + daNasa.size()
                + " novos=" + novos + " atualizados=" + atualizados, duracao));
        return new Resultado(daNasa.size(), novos, atualizados, duracao);
    }
}
