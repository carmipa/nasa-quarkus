package org.nasa.fatia.endereco.domain.ports;

import org.nasa.peer.geo.domain.Coordenada;

import java.util.Optional;

/**
 * Porta de saída: transformar um endereço escrito em um ponto no mapa.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Sem coordenada, o endereço do cliente não participa
 * do alerta de proximidade — o cálculo de distância até o evento natural depende dela.
 * Esta porta é o contrato mínimo de que a fatia precisa, e o motivo de a troca de
 * provedor ter custado uma linha de configuração quando o Google saiu do projeto em
 * 2026-09-02.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A porta não conhece provedor.</b> Nenhuma assinatura menciona Nominatim,
 *       BrasilAPI ou Photon. Quem conhece é o adaptador, em
 *       {@code fatia.endereco.infrastructure}.</li>
 *   <li><b>Não encontrar NÃO é falha.</b> Endereço inexistente ou ambíguo devolve
 *       {@link Optional#empty()}. Exceção fica reservada para o serviço indisponível —
 *       são situações diferentes e a fatia reage a cada uma de um jeito.</li>
 *   <li><b>Nunca devolver coordenada de fachada.</b> Vazio é vazio; jamais
 *       {@code (0,0)}, que poria o endereço no Golfo da Guiné com o mapa desenhando o
 *       pino lá e nenhum erro aparecendo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Provedor fora do ar ou recusando ⇒ o
 * adaptador lança a exceção de indisponibilidade da fatia, e o caso de uso decide se
 * degrada (salva o endereço sem coordenada, marcado) ou aborta. O que ele <b>não</b>
 * faz é inventar um ponto — endereço sem coordenada é salvo dizendo que está sem
 * coordenada, e a tela informa que ele não entra no alerta de proximidade.</p>
 */
public interface GeocodificacaoPort {

    /**
     * @param enderecoCompleto endereço em texto livre, já normalizado pela aplicação
     * @return o ponto correspondente, ou vazio quando o provedor não encontrou
     */
    Optional<Coordenada> geocodificar(String enderecoCompleto);
}
