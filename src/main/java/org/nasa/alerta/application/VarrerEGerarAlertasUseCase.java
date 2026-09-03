package org.nasa.alerta.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.DestinatarioProximo;
import org.nasa.alerta.domain.ports.RepositorioDeAlertasPort;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.geo.domain.Coordenada;
import org.nasa.geo.domain.Geodesia;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Descobre quem precisa ser avisado, e REGISTRA o aviso — sem enviar nada ainda.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o coração do sistema: cruza os eventos ativos da NASA
 * com os endereços cadastrados e produz a lista de quem está em risco. O resultado é uma
 * fila de avisos gravados, prontos para despachar.</p>
 *
 * <p><b>POR QUE REGISTRAR E ENVIAR SÃO ETAPAS SEPARADAS</b> (padrão <i>outbox</i>): se
 * este caso de uso enviasse direto, uma queda no meio da varredura deixaria parte das
 * pessoas avisadas e nenhum registro disso — e a próxima varredura avisaria todas de novo.
 * Gravando primeiro, o pior caso vira "avisar com atraso" em vez de "avisar duas vezes".
 * E, se o processo cair, os avisos já registrados continuam na fila.</p>
 *
 * <p><b>DUAS ETAPAS DE FILTRO, e a segunda é a que decide:</b></p>
 * <ol>
 *   <li><b>SQL, em graus</b> — recorte grosseiro que o banco resolve por índice;</li>
 *   <li><b>geodésia, em quilômetros</b> — a distância real sobre a esfera.</li>
 * </ol>
 * <p>Parar na primeira avisaria gente a até 41% além do raio pedido, porque grau não é
 * quilômetro e retângulo não é círculo. Quem é avisado à toa é quem desliga a notificação
 * antes do evento que importava.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-ALERTA-001 — o mesmo evento não avisa o mesmo cliente duas vezes.</b> A
 *       chave é {@code (cliente_id, evento_id)} e mora no banco. Uma tempestade que dura
 *       cinco dias aparece em cinco varreduras; sem a chave, seriam cinco avisos.</li>
 *   <li><b>Só contato de EMERGÊNCIA vira destinatário</b> — filtrado no SQL. Ninguém entra
 *       nessa lista sem ter sido inscrito nela.</li>
 *   <li><b>Rodar de novo é seguro e ESPERADO.</b> A varredura é feita para repetir; o que
 *       ela relata é quantos avisos são <b>novos</b>.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha de banco sobe com causa-raiz e a
 * varredura para — o que já foi registrado continua registrado, e a próxima execução
 * retoma sem duplicar.</p>
 */
@ApplicationScoped
public class VarrerEGerarAlertasUseCase {

    private static final Logger LOG = Logger.getLogger(VarrerEGerarAlertasUseCase.class);
    private static final String OPERACAO = "varrer-alertas";

    /**
     * Teto de pares trazidos pelo recorte grosseiro.
     *
     * <p>Generoso de propósito: cortar cedo descartaria pares que a geodésia aprovaria, e
     * o corte aconteceria em ordem de data — que não tem relação nenhuma com distância.</p>
     */
    private static final int CANDIDATOS_MAXIMOS = 5000;

    @Inject
    RepositorioDeAlertasPort repositorio;

    @Inject
    Relogio relogio;

    /**
     * O que a varredura fez.
     *
     * @param candidatos  pares trazidos pelo recorte em graus
     * @param dentroDoRaio quantos sobreviveram à geodésia  (a prova de que ela trabalha)
     * @param novos       avisos REGISTRADOS agora           (AGIU)
     * @param jaExistiam  avisos que já haviam sido dados    (ABSTEVE — e é o normal)
     * @param duracao     quanto levou
     */
    public record Resultado(int candidatos, int dentroDoRaio, int novos, int jaExistiam,
                            Duration duracao) {
    }

    /**
     * Varre e registra.
     *
     * @param raioKm raio do alerta, em quilômetros
     * @param dias   janela: eventos mais antigos não são considerados
     */
    public Resultado executar(double raioKm, int dias) {
        Instant inicio = relogio.agora();
        Instant desde = inicio.minus(Math.max(1, dias), ChronoUnit.DAYS);

        // ETAPA 1 — o recorte grosseiro, em graus, que o banco resolve por indice.
        List<RepositorioDeAlertasPort.Candidato> candidatos =
                repositorio.candidatos(raioKm, desde, CANDIDATOS_MAXIMOS);

        // ETAPA 2 — a geodesia, que DECIDE. Sem ela, o recorte em graus entrega gente
        // alem do raio, e quem e avisado a toa desliga a notificacao.
        List<DestinatarioProximo> dentro = new ArrayList<>();
        for (var c : candidatos) {
            double distancia = Geodesia.distanciaEmKm(
                    new Coordenada(c.latitudeInscrito(), c.longitudeInscrito()),
                    new Coordenada(c.latitudeEvento(), c.longitudeEvento()));
            if (distancia <= raioKm) {
                dentro.add(new DestinatarioProximo(c.inscritoId(), c.nomeInscrito(),
                        c.destino(), c.eventoId(), c.eventoTitulo(), distancia));
            }
        }

        // ETAPA 3 — registrar. A idempotencia e do BANCO: `ja existia` e o resultado
        // NORMAL de uma varredura repetida, nao um erro.
        int novos = 0;
        int jaExistiam = 0;
        for (DestinatarioProximo d : dentro) {
            boolean registrou = repositorio.registrarSeNovo(
                    Alerta.pendente(d.inscritoId(), d.eventoId(), d.destino()));
            if (registrou) {
                novos++;
                LOG.info(Registro.de(OPERACAO, "cliente=" + d.inscritoId(),
                        String.format("ALERTA REGISTRADO: %s a %.1f km de \"%s\"",
                                d.nomeInscrito(), d.distanciaKm(), d.eventoTitulo())));
            } else {
                jaExistiam++;
            }
        }

        Duration duracao = Duration.between(inicio, relogio.agora());
        // `descartados_pela_geodesia` e a prova de que a etapa 2 faz trabalho. Se um dia
        // ficar sempre zero, ou o recorte apertou demais ou a geodesia parou de filtrar —
        // e nenhuma das duas daria erro.
        LOG.info(Registro.de(OPERACAO, "raio=" + raioKm + "km",
                "candidatos=" + candidatos.size() + " dentro=" + dentro.size()
                        + " descartados_pela_geodesia=" + (candidatos.size() - dentro.size())
                        + " novos=" + novos + " ja_existiam=" + jaExistiam, duracao));

        return new Resultado(candidatos.size(), dentro.size(), novos, jaExistiam, duracao);
    }
}
