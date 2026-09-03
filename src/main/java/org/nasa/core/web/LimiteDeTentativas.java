package org.nasa.core.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quantas vezes uma mesma origem pode escrever numa janela de tempo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O formulário de inscrição é <b>público e escreve no
 * banco</b>. Medido em 03/09/2026: dez inscrições criadas em segundos, sem nada barrando —
 * e cada uma dispara chamadas à BrasilAPI e ao ViaCEP. O risco não é só a base encher: é o
 * projeto ser <b>bloqueado pelos provedores dos quais ele depende</b>.</p>
 *
 * <p><b>POR QUE NÃO É CAPTCHA.</b> Captcha exige serviço de terceiro, script externo e
 * cookie — três coisas que este projeto recusou em toda decisão. E ele resolve o problema
 * errado: barra robô e incomoda gente. O limite por origem barra <b>volume</b>, que é o que
 * causa dano, e é invisível para quem se inscreve uma vez.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>FALHA ABERTO, e é decisão declarada.</b> Se este contador quebrar, a inscrição
 *       <b>passa</b>. Um limitador com defeito que bloqueia todo mundo transforma uma
 *       proteção contra abuso numa negação de serviço construída por nós — e o dano de
 *       deixar passar é uma linha a mais no banco.</li>
 *   <li><b>A janela DESLIZA.</b> Uma janela fixa por hora deixa fazer o dobro do limite na
 *       virada: tudo no fim de uma hora e tudo no começo da seguinte.</li>
 *   <li><b>O teto de origens distintas existe.</b> Sem ele, um atacante com muitos IPs faz
 *       o mapa crescer sem limite — a proteção viraria o vazamento de memória.</li>
 *   <li><b>O relógio é INJETADO.</b> A catraca de UTC proíbe leitura direta, e é o que
 *       permite o teste provar a janela sem esperar de verdade.</li>
 * </ol>
 *
 * <p><b>SOBRE O IP: ele não é confiável, e o código não finge que é.</b> Atrás de proxy o
 * endereço real vem em {@code X-Forwarded-For} — um cabeçalho que <b>o cliente escreve</b>.
 * Confiar nele cegamente daria a qualquer um um limite novo por requisição. Aqui usa-se o
 * endereço da conexão, que o cliente não escolhe. O custo declarado: quem está atrás do
 * mesmo NAT divide o limite. Por isso o limite é generoso — ele barra flood, não uso.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer erro interno devolve "pode passar",
 * com WARN. Perder uma contagem é aceitável; recusar quem tinha direito não é.</p>
 */
@ApplicationScoped
public class LimiteDeTentativas {

    private static final Logger LOG = Logger.getLogger(LimiteDeTentativas.class);
    private static final String OPERACAO = "limitar-tentativas";

    /**
     * Teto de origens acompanhadas ao mesmo tempo.
     *
     * <p>Sem ele, um atacante com muitos endereços faz este mapa crescer sem limite — e a
     * proteção contra abuso vira o vazamento de memória que derruba o processo. No teto, as
     * origens novas <b>passam</b>: é a mesma escolha do item 1, e pelo mesmo motivo.</p>
     */
    static final int MAXIMO_DE_ORIGENS = 10_000;

    @ConfigProperty(name = "nasa.limite.tentativas", defaultValue = "10")
    int tentativasPermitidas;

    @ConfigProperty(name = "nasa.limite.janela-minutos", defaultValue = "10")
    int janelaEmMinutos;

    @Inject
    Relogio relogio;

    /** Os instantes recentes de cada origem. A fila é o que faz a janela deslizar. */
    private final Map<String, Deque<Instant>> porOrigem = new ConcurrentHashMap<>();

    /**
     * Registra a tentativa e diz se ela pode seguir.
     *
     * @param origem identificação da origem — o endereço da conexão, nunca um cabeçalho
     *               que o cliente escreve
     * @return {@code true} se pode seguir. <b>Erro interno também devolve {@code true}</b>
     */
    public boolean podeSeguir(String origem) {
        try {
            if (origem == null || origem.isBlank()) {
                // Sem origem nao ha o que contar. Recusar aqui bloquearia chamadas
                // internas e testes — que sao exatamente quem nao tem endereco remoto.
                return true;
            }
            var agora = relogio.agora();
            var limiteDaJanela = agora.minus(Duration.ofMinutes(Math.max(1, janelaEmMinutos)));

            var fila = porOrigem.get(origem);
            if (fila == null) {
                if (porOrigem.size() >= MAXIMO_DE_ORIGENS) {
                    // No teto, PASSA — e avisa. A alternativa seria recusar quem chega
                    // depois, transformando o limitador na negacao de servico.
                    LOG.warn(Registro.recusa(OPERACAO, "mapa",
                            "TETO_DE_ORIGENS_" + MAXIMO_DE_ORIGENS));
                    return true;
                }
                fila = porOrigem.computeIfAbsent(origem, k -> new ArrayDeque<>());
            }

            synchronized (fila) {
                // Descarta o que saiu da janela ANTES de contar — e isto e o deslizamento.
                while (!fila.isEmpty() && fila.peekFirst().isBefore(limiteDaJanela)) {
                    fila.pollFirst();
                }
                if (fila.size() >= Math.max(1, tentativasPermitidas)) {
                    // A origem NAO e registrada na mensagem: endereco de rede identifica
                    // pessoa, e log e lido por quem nao precisa disso. O que interessa e
                    // que houve recusa, e quantas.
                    LOG.warn(Registro.recusa(OPERACAO, "origem",
                            "LIMITE_ATINGIDO_" + tentativasPermitidas + "_EM_"
                                    + janelaEmMinutos + "MIN"));
                    return false;
                }
                fila.addLast(agora);
                return true;
            }
        } catch (RuntimeException naoContou) {
            // FALHA ABERTO. Um limitador com defeito que bloqueia todo mundo transforma
            // uma protecao contra abuso numa negacao de servico construida por nos.
            LOG.warn(Registro.recusa(OPERACAO, "contador", "NAO_CONTOU_PASSANDO"), naoContou);
            return true;
        }
    }

    /**
     * Quantas tentativas cabem na janela.
     *
     * <p><b>É MÉTODO, e não campo público, por um motivo medido em 03/09/2026.</b> Esta
     * classe é {@code @ApplicationScoped}, então o CDI entrega um <b>proxy</b>. Ler um campo
     * direto no proxy <b>não delega</b>: devolve o valor padrão do tipo — {@code 0} para
     * {@code int} — em vez do configurado. O primeiro teste leu o campo, recebeu zero, e
     * reprovou sem que houvesse defeito no limitador.</p>
     *
     * <p>Chamada de método atravessa o proxy. É a diferença entre o teste medir o objeto e
     * medir a casca dele.</p>
     */
    public int tentativasPermitidas() {
        return tentativasPermitidas;
    }

    /** Quantas origens estão sendo acompanhadas — a telemetria e o teste usam isto. */
    public int origensAcompanhadas() {
        return porOrigem.size();
    }

    /** Esquece tudo. Existe para o teste começar de um estado conhecido. */
    public void esquecerTudo() {
        porOrigem.clear();
    }
}
