package org.nasa.core.log;

import java.time.Duration;

/**
 * Monta a linha de log no formato canônico — mecanismo, não convenção.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A planta (§9.2) exige que toda linha carregue
 * <b>operação</b>, <b>alvo</b> e, quando houver, <b>duração</b>. Deixar isso como
 * combinado verbal produz metade das linhas completas e metade não — e é justamente a
 * metade incompleta que aparece no dia do incidente. Esta classe transforma o formato em
 * código: quem chama não tem como esquecer um campo, porque ele é parâmetro.</p>
 *
 * <p>O formato produzido, que se encaixa depois de {@code <instante> <nível>
 * [execucaoId] [origem]} montados pelo próprio Quarkus:</p>
 * <pre>
 * &lt;operação&gt; alvo=&lt;alvo&gt; — &lt;mensagem&gt; (&lt;duração&gt;)
 * sincronizar-nasa alvo=EONET_1001 — 42 eventos gravados (1,3s)
 * </pre>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Toda recusa carrega o MOTIVO.</b> É a regra que mais acha bug: um
 *       {@code motivo} calculado e jogado fora cegou 996 pendências no projeto de origem.
 *       Não é {@code WARN "item pulado"} — é {@code WARN "item pulado alvo=x
 *       motivo=SEM_COORDENADA"}.</li>
 *   <li><b>Toda linha nomeia o ALVO.</b> Um <i>"[APLICADO]"</i> sem alvo, num console que
 *       mistura execuções, já fez um resultado alheio parecer o próprio.</li>
 *   <li><b>Nunca formatar segredo, token, credencial ou caminho absoluto da máquina do
 *       usuário.</b> Caminho absoluto vaza PII, e um deles já custou reescrita de
 *       histórico de repositório. O alvo é o identificador de negócio, não o caminho.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Campo obrigatório vazio <b>não lança</b> — um
 * erro de formatação não pode derrubar a operação que estava sendo registrada. Em vez
 * disso o campo vira {@code NAO_INFORMADO}, que é ruidoso na leitura e <b>greppável</b>:
 * {@code grep NAO_INFORMADO logs/} lista exatamente as chamadas que precisam de conserto.
 * Falhar alto aqui trocaria um defeito de log por uma queda de serviço — a lente de
 * boa-fé aplicada à própria ferramenta.</p>
 */
public final class Registro {

    /** Marca greppável de campo obrigatório que o chamador esqueceu. */
    public static final String NAO_INFORMADO = "NAO_INFORMADO";

    private Registro() {
    }

    /** Linha de operação concluída, sem medição de tempo. */
    public static String de(String operacao, String alvo, String mensagem) {
        return montar(operacao, alvo, mensagem, null);
    }

    /** Linha de operação concluída, com a duração medida. */
    public static String de(String operacao, String alvo, String mensagem, Duration duracao) {
        return montar(operacao, alvo, mensagem, duracao);
    }

    /**
     * Linha de recusa, pulo ou degradação — <b>sempre</b> com o motivo.
     *
     * @param motivo causa em caixa alta e sem espaço ({@code SEM_COORDENADA},
     *               {@code PROVEDOR_INDISPONIVEL}), para agrupar por causa depois
     */
    public static String recusa(String operacao, String alvo, String motivo) {
        return montar(operacao, alvo, "motivo=" + preencher(motivo), null);
    }

    private static String montar(String operacao, String alvo, String mensagem, Duration duracao) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(preencher(operacao))
          .append(" alvo=").append(preencher(alvo))
          .append(" — ").append(preencher(mensagem));
        if (duracao != null) {
            sb.append(" (").append(formatar(duracao)).append(')');
        }
        return sb.toString();
    }

    private static String preencher(String valor) {
        return (valor == null || valor.isBlank()) ? NAO_INFORMADO : valor;
    }

    /**
     * Duração legível.
     *
     * <p>Abaixo de um segundo em milissegundos; acima, em segundos com uma casa. Achar a
     * lentidão exige o número na linha, não a suspeita.</p>
     */
    static String formatar(Duration duracao) {
        long ms = duracao.toMillis();
        if (ms < 1000) {
            return ms + "ms";
        }
        return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0);
    }
}
