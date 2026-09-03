package org.nasa.alerta.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O serviço de geocodificação não respondeu — ou recusou por política de uso.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Distingue "não achei este endereço no mapa" (resultado
 * normal, {@code Optional.empty()}) de "não consegui perguntar". A diferença decide o que
 * o sistema faz: no primeiro caso o endereço é salvo sem coordenada e fica de fora do
 * alerta <b>para sempre</b>; no segundo, ele deve ser tentado de novo depois.</p>
 *
 * <p><b>O caso que merece atenção</b> é o HTTP 429/403 do Nominatim público: é bloqueio
 * por exceder a política de uso, dura horas e atinge o IP inteiro. Ele chega disfarçado
 * de "o serviço está fora", e sem esta distinção no log ninguém liga o sintoma à causa —
 * que foi um laço nosso pedindo rápido demais.</p>
 *
 * <p><b>INVARIANTE.</b> Falha do provedor <b>não</b> derruba o cadastro: o caso de uso
 * salva o endereço sem coordenada, marcado, e a tela diz que ele não entra no alerta de
 * proximidade. Recusar o cadastro inteiro por causa de serviço de terceiro seria pior.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#PROVEDOR_INDISPONIVEL} — vira 503 quando
 * chega à borda, que é o status que diz "tente de novo".</p>
 */
public class ProvedorDeGeocodificacaoIndisponivelException extends ErroDePipeline {
    public ProvedorDeGeocodificacaoIndisponivelException(String alvo, Throwable causaTecnica) {
        super("geocodificar", alvo, CausaRaiz.PROVEDOR_INDISPONIVEL,
              "o servico de geocodificacao nao respondeu ou recusou por politica de uso",
              causaTecnica);
    }
}
