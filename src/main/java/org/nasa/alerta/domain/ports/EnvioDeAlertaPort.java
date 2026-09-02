package org.nasa.alerta.domain.ports;

import org.nasa.alerta.domain.Alerta;

/**
 * Por onde o aviso sai.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Isola o caso de uso do meio de entrega. Hoje não há
 * servidor de e-mail configurado, e o adaptador em uso <b>registra no log</b> em vez de
 * enviar. A porta existe para que trocar isso por SMTP seja um adaptador novo, e não uma
 * cirurgia no fluxo de despacho.</p>
 *
 * <p><b>ESTA LACUNA É DECLARADA, e não escondida:</b> enquanto o adaptador for o de log,
 * <b>ninguém recebe aviso nenhum</b> — o sistema apenas registra que teria enviado. Um
 * adaptador que fingisse sucesso silencioso seria pior que não ter alerta, porque a tela
 * mostraria "ENVIADO" e a pessoa não receberia nada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Falha é EXCEÇÃO, nunca {@code false} silencioso.</b> Um retorno booleano
 *       ignorado é a forma mais fácil de um aviso sumir sem rastro.</li>
 *   <li><b>{@link #descricaoDoMeio()} diz o que está em uso</b>, e a tela mostra. Sem
 *       isso, ninguém distingue "enviado por e-mail" de "registrado no log".</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Lança exceção com causa-raiz; o despacho
 * marca o alerta como {@code FALHOU} com a causa, e a linha continua no banco para
 * auditoria — nunca desaparece.</p>
 */
public interface EnvioDeAlertaPort {

    /**
     * Entrega o aviso.
     *
     * @throws org.nasa.core.erro.ErroDePipeline quando não foi possível entregar
     */
    void enviar(Alerta alerta, String assunto, String mensagem);

    /** O que está realmente em uso — vai para a tela e para o log. */
    String descricaoDoMeio();

    /** {@code false} quando o meio atual não entrega de verdade a ninguém. */
    boolean entregaDeVerdade();
}
