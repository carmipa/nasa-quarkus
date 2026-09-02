package org.nasa.evento.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;
import org.nasa.geo.domain.CaixaDelimitadora;
import org.nasa.geo.domain.Coordenada;
import org.nasa.geo.domain.Geodesia;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Quais eventos naturais estão perto deste ponto — a pergunta que dispara o alerta.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o cálculo que decide se alguém é avisado. Errar aqui
 * tem duas formas, e as duas são caras: avisar quem está longe (e ensinar a pessoa a
 * ignorar o aviso) ou calar para quem está perto.</p>
 *
 * <p><b>DUAS ETAPAS, E A SEGUNDA É A QUE DECIDE.</b></p>
 * <ol>
 *   <li><b>Caixa delimitadora</b> — um retângulo em graus, que o banco resolve por índice.
 *       Reduz a base inteira a dezenas de candidatos.</li>
 *   <li><b>Geodésia</b> — a distância real sobre a esfera, que decide quem fica.</li>
 * </ol>
 * <p>Parar na primeira etapa seria o erro tentador, porque "já filtrou". Mas caixa é
 * <b>retângulo</b> e raio é <b>círculo</b>: o canto do retângulo fica a
 * {@code raio × √2} do centro — <b>41% mais longe</b> que o raio pedido. Num alerta de
 * 100 km, isso avisa gente a 141 km, e a pessoa avisada à toa é a que desliga a
 * notificação antes do evento que importava.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Só evento ATIVO e COM coordenada.</b> Encerrado não avisa ninguém; sem posição
 *       não há o que comparar. O repositório filtra os dois no SQL.</li>
 *   <li><b>Ordenado pelo MAIS PRÓXIMO.</b> Quem lê a lista precisa ver primeiro o que está
 *       em cima dele, não o mais recente que está a 90 km.</li>
 *   <li><b>A distância vai JUNTO na resposta.</b> Devolver só a lista obrigaria a tela a
 *       recalcular — e um segundo cálculo é um segundo lugar para divergir.</li>
 *   <li><b>Raio inválido é recusado pelo peer {@code geo}</b>, não por uma checagem aqui.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Lista vazia é resposta legítima — significa
 * "nada perto", que é a resposta desejável na maior parte dos dias. Falha de banco sobe
 * com causa-raiz.</p>
 */
@ApplicationScoped
public class EventosProximosUseCase {

    private static final Logger LOG = Logger.getLogger(EventosProximosUseCase.class);
    private static final String OPERACAO = "eventos-proximos";

    /**
     * Teto de candidatos trazidos pela caixa.
     *
     * <p>Protege a memória, e é generoso de propósito: cortar cedo demais faria a caixa
     * descartar um evento que a geodésia teria aprovado — e o corte aconteceria em ordem
     * de data, que não tem relação nenhuma com distância.</p>
     */
    private static final int CANDIDATOS_MAXIMOS = 500;

    @Inject
    RepositorioDeEventosPort repositorio;

    @Inject
    ConsultarEventosUseCase consultar;

    /**
     * Um evento e a que distância ele está.
     *
     * @param evento     o evento
     * @param distanciaKm distância real sobre a esfera, em quilômetros
     */
    public record EventoProximo(EventoNatural evento, double distanciaKm) {
    }

    /**
     * Os eventos ativos dentro do raio, do mais perto para o mais longe.
     *
     * @param centro  o ponto de referência — o endereço do cliente
     * @param raioKm  o raio do alerta
     * @param dias    janela para trás; eventos mais antigos não são considerados
     */
    public List<EventoProximo> executar(Coordenada centro, double raioKm, int dias) {
        CaixaDelimitadora caixa = CaixaDelimitadora.emVoltaDe(centro, raioKm);
        Instant desde = consultar.inicioDaJanela(dias);

        // ETAPA 1 — a caixa, por indice. Traz candidatos A MAIS, de proposito.
        List<EventoNatural> candidatos =
                repositorio.ativosNaCaixa(caixa, desde, CANDIDATOS_MAXIMOS);

        // ETAPA 2 — a geodesia, que DECIDE. Sem ela, o canto da caixa entrega eventos a
        // ate 41% alem do raio pedido, e quem e avisado a toa desliga a notificacao.
        List<EventoProximo> dentro = candidatos.stream()
                .filter(e -> e.coordenada() != null)
                .map(e -> new EventoProximo(e,
                        Geodesia.distanciaEmKm(centro, e.coordenada())))
                .filter(p -> p.distanciaKm() <= raioKm)
                .sorted(Comparator.comparingDouble(EventoProximo::distanciaKm))
                .toList();

        // A diferenca entre as duas etapas e a prova de que a segunda faz trabalho. Se um
        // dia ela ficar sempre zero, ou a caixa apertou demais ou a geodesia parou de
        // filtrar — e nenhuma das duas daria erro.
        LOG.info(Registro.de(OPERACAO, centro.toString(),
                "raio=" + raioKm + "km candidatos=" + candidatos.size()
                        + " dentro=" + dentro.size()
                        + " descartados_pela_geodesia=" + (candidatos.size() - dentro.size())));
        return dentro;
    }
}
