package org.nasa.core.erro;

import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.telemetria.ContadorDeCausaRaiz;

/**
 * O único lugar onde uma falha vira <b>log</b> e <b>telemetria</b> — exatamente uma vez.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Ordem de Paulo (2026-09-02): cada classe com exceção
 * específica, com log e telemetria. A exceção específica já existe e já carrega operação,
 * alvo e causa-raiz ({@link ErroDePipeline}); esta classe é o outro lado do contrato — o
 * ponto em que essa carga vira uma linha no arquivo de log e um número no painel.</p>
 *
 * <p><b>POR QUE AQUI E NÃO NO CONSTRUTOR DA EXCEÇÃO.</b> Uma exceção que se registra ao
 * nascer parece resolver e cria dois defeitos: ela é registrada mesmo quando alguém a
 * captura e trata com sucesso (ERROR falso no painel), e é registrada de novo a cada
 * reembrulho na subida da pilha (o mesmo incidente contado três vezes). Registrar no
 * ponto em que a falha <b>venceu</b> — a borda, o worker, o laço — conta uma vez o que
 * aconteceu uma vez.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Log e telemetria andam juntos.</b> Não existe registrar só um: log sem contagem
 *       não acusa sozinho, e contagem sem log não diz o que houve.</li>
 *   <li><b>{@link CausaRaiz#INTERROMPIDO} é WARN, não ERROR.</b> Parada pedida pelo
 *       operador é o botão funcionando; contá-la como erro faz o painel acusar defeito
 *       toda vez que alguém aperta "Parar".</li>
 *   <li><b>Falha da telemetria não derruba nada.</b> Se o contador explodir, isso vira uma
 *       linha de WARN e a falha original segue registrada — a segunda mensagem nunca
 *       apaga a primeira.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Nunca lança. É o último elo antes de o
 * incidente virar número, e um elo que quebra aqui apaga a evidência de tudo que veio
 * antes.</p>
 */
public final class RegistradorDeFalha {

    private static final Logger LOG = Logger.getLogger(RegistradorDeFalha.class);

    private RegistradorDeFalha() {
    }

    /**
     * Registra a falha: uma linha de log no formato canônico e uma contagem causal.
     *
     * @param erro     a falha específica, já com operação, alvo e causa-raiz
     * @param contador a telemetria da fatia; pode ser {@code null} quando a fatia ainda
     *                 não tem porta — e nesse caso o log sai igual, apenas sem a contagem
     */
    public static void registrar(ErroDePipeline erro, ContadorDeCausaRaiz contador) {
        if (erro == null) {
            LOG.warn(Registro.recusa("registrar-falha", "erro-nulo",
                    CausaRaiz.NAO_CLASSIFICADA.name()));
            return;
        }

        if (erro.causaRaiz() == CausaRaiz.INTERROMPIDO) {
            LOG.warn(erro.linhaDeLog());
        } else {
            LOG.error(erro.linhaDeLog(), erro);
        }

        if (contador == null) {
            return;
        }
        try {
            contador.contar(erro.causaRaiz());
        } catch (RuntimeException falhaDoContador) {
            // "Falhei ao contar que falhei" nunca pode esconder a falha original.
            LOG.warn(Registro.recusa("contar-causa-raiz", erro.operacao(),
                    CausaRaiz.NAO_CLASSIFICADA.name()), falhaDoContador);
        }
    }

    /** Atalho para quando a fatia ainda não tem porta de telemetria. */
    public static void registrar(ErroDePipeline erro) {
        registrar(erro, null);
    }
}
