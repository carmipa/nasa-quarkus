package org.nasa.inscrito.domain.ports;

import org.nasa.inscrito.domain.Cep;
import org.nasa.geo.domain.Coordenada;

import java.util.Optional;

/**
 * Porta de saída: descobrir o endereço a partir do CEP.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que transforma oito dígitos em rua, bairro, cidade
 * e — quando o provedor tem — o ponto no mapa. A porta existe para que a fatia não conheça
 * nenhum provedor: hoje são BrasilAPI e ViaCEP, e trocar qualquer um é trocar adaptador.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>CEP inexistente NÃO é falha.</b> Devolve {@link Optional#empty()}. Exceção
 *       fica reservada para provedor indisponível — são situações diferentes e a fatia
 *       reage a cada uma de um jeito: a primeira o operador corrige, a segunda ele
 *       repete.</li>
 *   <li><b>A coordenada é opcional dentro do resultado.</b> Medido: 5 de 6 CEPs voltam com
 *       ela; o sexto veio pelo provedor dos Correios, sem coordenada e sem nem a cidade.
 *       O adaptador nunca inventa o que não veio.</li>
 *   <li><b>Nenhuma assinatura menciona provedor, HTTP ou JSON.</b></li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Indisponibilidade ⇒
 * {@code ProvedorDeEnderecoIndisponivelException}, que vira 503 ("tente de novo") e nunca
 * 404 ("desista") — devolver "CEP não encontrado" quando o provedor caiu faz a pessoa
 * apagar um CEP que estava certo.</p>
 */
public interface ConsultaCepPort {

    /**
     * O que um provedor de CEP sabe dizer.
     *
     * @param coordenada vazio quando o provedor não trouxe — nunca {@code (0,0)}
     */
    record EnderecoDoCep(Cep cep, String logradouro, String bairro, String localidade,
                         String uf, Optional<Coordenada> coordenada, String provedor) {
    }

    /** O endereço daquele CEP, ou vazio se ele não existe. */
    Optional<EnderecoDoCep> consultar(Cep cep);

    /** Nome do provedor, para telemetria e log. */
    String nome();
}
