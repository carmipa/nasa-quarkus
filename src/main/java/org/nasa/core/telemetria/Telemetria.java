package org.nasa.core.telemetria;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O coletor de telemetria do sistema — quantas vezes cada operação rodou, e quanto levou.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O log responde <i>"o que aconteceu naquele momento"</i>.
 * Ele não responde <i>"com que frequência isso acontece"</i>, <i>"está mais lento que
 * ontem"</i> nem <i>"a NASA caiu quantas vezes hoje"</i>. Essas são perguntas de agregado,
 * e respondê-las lendo log é grep com aritmética à mão — que ninguém faz às três da manhã,
 * que é quando a pergunta aparece.</p>
 *
 * <p><b>ACUMULA EM MEMÓRIA, DESCARREGA PERIODICAMENTE.</b> Uma ida ao banco por operação
 * executada poria latência de escrita dentro de cada chamada observada — e telemetria é
 * apoio: apoio que cobra pedágio da função que observa acaba desligado no dia em que o
 * sistema fica lento, que é justamente quando ele mais serve.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>NUNCA lança para quem chama.</b> Um defeito de telemetria não pode derrubar a
 *       operação de negócio que estava sendo medida. É a mesma disciplina do
 *       {@code Registro}: falhar alto aqui trocaria um problema de observabilidade por
 *       uma queda de serviço.</li>
 *   <li><b>RECUSA e FALHA são contadas SEPARADAMENTE.</b> Recusa é o sistema decidindo não
 *       fazer, e sabendo por quê (CEP inválido, coordenada fora da Terra); falha é algo
 *       que quebrou. Somá-las apagaria a diferença, e "1000 erros" mandaria investigar
 *       infraestrutura quando o problema é o dado que chega.</li>
 *   <li><b>A hora é truncada em UTC</b>, pelo relógio injetado. Fuso local faria a mesma
 *       chamada cair em horas diferentes por máquina — a mesma família de defeito do log
 *       em {@code -03:00}.</li>
 *   <li><b>SOMA, MÍNIMO e MÁXIMO, nunca média.</b> Média de médias está errada: agregar
 *       duas horas com 1 e 1000 chamadas dando peso igual mente. Com soma e contagem, a
 *       média de qualquer janela sai certa — e o máximo revela o caso ruim que a média
 *       esconde, que é o caso que derruba o sistema.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer erro vira WARN e a medição é
 * descartada. Perder uma medição é aceitável; derrubar a operação medida não é.</p>
 */
@ApplicationScoped
public class Telemetria {

    private static final Logger LOG = Logger.getLogger(Telemetria.class);
    private static final String OPERACAO = "telemetria";

    /**
     * Teto de operações distintas mantidas em memória.
     *
     * <p><b>Existe porque o nome da operação poderia vir de fora um dia.</b> Hoje ele é
     * constante de código, mas um mapa sem teto alimentado por texto externo é vazamento
     * de memória esperando acontecer — e o dia em que alguém passar o caminho da URL como
     * nome de operação, o teto é o que impede a aplicação cair.</p>
     */
    static final int MAXIMO_DE_OPERACOES = 500;

    @Inject
    Relogio relogio;

    /**
     * O acumulado, por (operação, hora).
     *
     * <p>{@link ConcurrentHashMap} porque requisições HTTP são concorrentes por natureza:
     * dois pedidos simultâneos à mesma rota incrementam o mesmo contador, e um
     * {@code HashMap} comum perderia contagem <b>ou</b> entraria em laço infinito no
     * redimensionamento. Perder telemetria seria ruim; travar uma requisição por causa
     * dela seria inaceitável.</p>
     */
    private final Map<Chave, Acumulado> acumulado = new ConcurrentHashMap<>();

    /** A chave de agregação: uma operação, numa hora. */
    record Chave(String operacao, Instant hora) {
    }

    /**
     * O que se sabe de uma operação numa hora.
     *
     * <p>Mutável e sincronizado internamente: é o objeto que recebe incremento concorrente.
     * Os campos são lidos só na descarga, sob o mesmo bloqueio.</p>
     */
    static final class Acumulado {
        private long chamadas;
        private long recusas;
        private long falhas;
        private long duracaoSomaMs;
        private long duracaoMinMs = Long.MAX_VALUE;
        private long duracaoMaxMs;

        synchronized void somar(long ms, Desfecho desfecho) {
            chamadas++;
            switch (desfecho) {
                case RECUSA -> recusas++;
                case FALHA -> falhas++;
                case SUCESSO -> { }
            }
            if (ms >= 0) {
                duracaoSomaMs += ms;
                duracaoMinMs = Math.min(duracaoMinMs, ms);
                duracaoMaxMs = Math.max(duracaoMaxMs, ms);
            }
        }

        synchronized Medida congelar(String operacao, Instant hora) {
            return new Medida(operacao, hora, chamadas, recusas, falhas, duracaoSomaMs,
                    duracaoMinMs == Long.MAX_VALUE ? 0 : duracaoMinMs, duracaoMaxMs);
        }
    }

    /** Como a operação terminou. */
    public enum Desfecho {
        /** Fez o que deveria. */
        SUCESSO,
        /** Decidiu NÃO fazer, e sabe por quê — entrada inválida, regra de negócio. */
        RECUSA,
        /** Quebrou: fonte externa fora, banco recusou, contrato mudou. */
        FALHA
    }

    /**
     * Uma medida pronta para gravar.
     *
     * @param operacao      no mesmo vocabulário do log
     * @param hora          truncada, em UTC
     * @param chamadas      total
     * @param recusas       quantas foram recusa deliberada
     * @param falhas        quantas quebraram
     * @param duracaoSomaMs soma das durações; a média de qualquer janela sai daqui
     * @param duracaoMinMs  a mais rápida
     * @param duracaoMaxMs  a mais lenta — o caso que a média esconde
     */
    public record Medida(String operacao, Instant hora, long chamadas, long recusas,
                         long falhas, long duracaoSomaMs, long duracaoMinMs,
                         long duracaoMaxMs) {

        /** A média da janela. Calculada, nunca guardada — média guardada não se soma. */
        public long duracaoMediaMs() {
            return chamadas == 0 ? 0 : duracaoSomaMs / chamadas;
        }
    }

    // ------------------------------------------------------------------ registro

    /** Registra uma operação bem-sucedida com a duração medida. */
    public void sucesso(String operacao, Duration duracao) {
        registrar(operacao, duracao, Desfecho.SUCESSO);
    }

    /** Registra uma recusa deliberada — entrada inválida, regra de negócio. */
    public void recusa(String operacao, Duration duracao) {
        registrar(operacao, duracao, Desfecho.RECUSA);
    }

    /** Registra uma falha — fonte externa fora, banco recusou, contrato mudou. */
    public void falha(String operacao, Duration duracao) {
        registrar(operacao, duracao, Desfecho.FALHA);
    }

    /**
     * O registro em si.
     *
     * <p><b>Envolvido em {@code try/catch} do começo ao fim</b>, e o {@code catch} é
     * {@code RuntimeException} de propósito: não há erro de telemetria que justifique
     * derrubar a operação medida.</p>
     */
    public void registrar(String operacao, Duration duracao, Desfecho desfecho) {
        try {
            if (operacao == null || operacao.isBlank()) {
                // Operacao sem nome nao vira linha anonima: linha anonima e ruido que
                // ninguem consegue investigar depois.
                LOG.warn(Registro.recusa(OPERACAO, "medida", "OPERACAO_SEM_NOME"));
                return;
            }
            var chave = new Chave(operacao, horaAtual());

            // O teto vale para CHAVES NOVAS. Uma chave que ja existe continua sendo
            // incrementada mesmo no teto — parar de contar o que ja se conta produziria
            // um grafico que estanca sem avisar.
            var existente = acumulado.get(chave);
            if (existente == null && acumulado.size() >= MAXIMO_DE_OPERACOES) {
                LOG.warn(Registro.recusa(OPERACAO, operacao,
                        "TETO_DE_OPERACOES_" + MAXIMO_DE_OPERACOES));
                return;
            }

            long ms = duracao == null ? -1 : Math.max(0, duracao.toMillis());
            acumulado.computeIfAbsent(chave, k -> new Acumulado()).somar(ms, desfecho);
        } catch (RuntimeException naoRegistrou) {
            // Perder uma medicao e aceitavel. Derrubar a operacao medida nao e.
            LOG.warn(Registro.recusa(OPERACAO, String.valueOf(operacao), "NAO_REGISTROU"),
                    naoRegistrou);
        }
    }

    /**
     * Mede o que o bloco levou e registra sozinho — inclusive quando ele lança.
     *
     * <p><b>O {@code finally} é o ponto desta assinatura.</b> Medir só o caminho feliz
     * produz telemetria que fica bonita justamente quando o sistema está quebrando: o
     * gráfico mostraria menos chamadas e menor latência à medida que mais coisas falham.
     * Aqui a exceção é contada como falha, com o tempo que ela levou até estourar.</p>
     */
    public <T> T medir(String operacao, java.util.function.Supplier<T> bloco) {
        var inicio = relogio.agora();
        var desfecho = Desfecho.SUCESSO;
        try {
            return bloco.get();
        } catch (RuntimeException falhou) {
            desfecho = Desfecho.FALHA;
            throw falhou;
        } finally {
            registrar(operacao, Duration.between(inicio, relogio.agora()), desfecho);
        }
    }

    // ------------------------------------------------------------------ descarga

    /**
     * Retira e devolve tudo o que está acumulado.
     *
     * <p><b>Retira</b>, e não copia: o que sai daqui vai ser somado no banco, e deixar a
     * cópia em memória faria a próxima descarga somar de novo os mesmos números. Contagem
     * dobrada num gráfico é pior que contagem ausente — ela não parece defeito.</p>
     *
     * <p>Entre o {@code remove} e a soma no banco há uma janela em que a medição não está
     * em lugar nenhum. Se o processo cair exatamente ali, perde-se até um ciclo de
     * telemetria — e essa é a troca escolhida, declarada: a alternativa (só remover depois
     * de gravar) duplicaria a contagem quando a gravação tivesse sucesso mas a confirmação
     * se perdesse, e dado dobrado engana mais que dado faltando.</p>
     */
    public List<Medida> retirarTudo() {
        List<Medida> medidas = new ArrayList<>(acumulado.size());
        for (var chave : List.copyOf(acumulado.keySet())) {
            var valor = acumulado.remove(chave);
            if (valor != null) {
                medidas.add(valor.congelar(chave.operacao(), chave.hora()));
            }
        }
        return medidas;
    }

    /** Quantas chaves estão acumuladas agora — a página de telemetria mostra isto. */
    public int pendentes() {
        return acumulado.size();
    }

    /** A hora atual truncada, em UTC, pelo relógio injetado. */
    private Instant horaAtual() {
        return relogio.agora().truncatedTo(ChronoUnit.HOURS);
    }
}
