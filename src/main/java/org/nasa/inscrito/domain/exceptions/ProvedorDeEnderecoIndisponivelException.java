package org.nasa.inscrito.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Nenhum provedor externo respondeu — nem o primário, nem a reserva.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Distingue "o CEP não existe" de "não consegui
 * perguntar". Os dois terminam sem endereço na tela, e confundi-los é caro: o primeiro é
 * erro de digitação e a pessoa corrige; o segundo é indisponibilidade e ela deveria
 * tentar de novo em um minuto. Devolver "CEP não encontrado" quando o provedor caiu faz
 * a pessoa apagar um CEP que estava certo.</p>
 *
 * <p><b>INVARIANTE.</b> Só é lançada depois de <b>todos</b> os provedores falharem. Um
 * provedor fora do ar cai para o seguinte — a degradação é declarada, não silenciosa.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#PROVEDOR_INDISPONIVEL} — vira 503, que é o
 * status que diz "tente de novo", e não 404, que diz "desista".</p>
 */
public class ProvedorDeEnderecoIndisponivelException extends ErroDePipeline {
    public ProvedorDeEnderecoIndisponivelException(String alvo, Throwable causaTecnica) {
        super("consultar-cep", alvo, CausaRaiz.PROVEDOR_INDISPONIVEL,
              "nenhum provedor de endereco respondeu", causaTecnica);
    }
}
