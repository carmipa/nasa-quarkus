package org.nasa.endereco.domain;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.endereco.domain.exceptions.EnderecoInvalidoException;
import org.nasa.geo.domain.Coordenada;

import java.time.Instant;
import java.util.Optional;

/**
 * Um endereço — e, quando se sabe, o ponto dele no mapa.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que liga uma pessoa a um lugar. O alerta de
 * desastre só funciona para endereços que têm <b>coordenada</b>: sem ela não há distância
 * até o evento, e a pessoa simplesmente não é avisada.</p>
 *
 * <p><b>A INVARIANTE QUE CUSTA MAIS CARO.</b> Coordenada ausente é <b>ausente</b>, e o
 * tipo diz isso: {@link Optional}. Medido em 2026-09-02: de 6 CEPs consultados na
 * BrasilAPI, <b>5 vieram com coordenada e 1 não</b> — o caso é comum, não excepcional.
 * Preencher o que faltou com {@code (0,0)} poria o endereço no Golfo da Guiné, o mapa
 * desenharia o pino lá, o cálculo de distância daria um número plausível, e
 * <b>nenhum erro apareceria</b>. É por isso que existe {@code CHECK} no banco recusando
 * o par exato, além deste tipo.</p>
 *
 * <p><b>OUTRAS INVARIANTES.</b></p>
 * <ol>
 *   <li><b>Complemento é opcional</b> — a maioria dos endereços do Brasil não tem. No
 *       legado era {@code NOT NULL}, e a regra obrigava o operador a inventar valor.</li>
 *   <li><b>UF tem duas letras</b>, em maiúsculas. É o campo que mais chega sujo.</li>
 *   <li><b>Logradouro e localidade não são vazios</b>: endereço sem rua nem cidade não
 *       serve nem para conferência humana.</li>
 *   <li><b>Domínio puro</b>: sem framework, sem I/O.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Construtor recusa campo obrigatório vazio com
 * {@link EnderecoInvalidoException} ({@link CausaRaiz#DADO_INVALIDO}); endereço inválido
 * nunca chega a existir como objeto.</p>
 *
 * @param id          nulo enquanto não gravado
 * @param cep         normalizado
 * @param numero      nulo quando o logradouro não tem numeração
 * @param complemento opcional, como no mundo real
 * @param coordenada  vazio quando a origem não tinha — nunca (0,0)
 * @param criadoEm    instante UTC; nulo enquanto não gravado
 */
public record Endereco(Long id, Cep cep, Integer numero, String logradouro, String bairro,
                       String localidade, String uf, String complemento,
                       Optional<Coordenada> coordenada, Instant criadoEm) {

    public Endereco {
        if (cep == null) {
            throw new EnderecoInvalidoException("cep", "ausente");
        }
        logradouro = exigir(logradouro, "logradouro");
        localidade = exigir(localidade, "localidade");
        uf = exigir(uf, "uf").toUpperCase();
        if (uf.length() != 2) {
            throw new EnderecoInvalidoException("uf", "esperado 2 letras, recebi " + uf);
        }
        coordenada = coordenada == null ? Optional.empty() : coordenada;
        // Cinto e suspensório: o banco tem CHECK para isto, e o domínio também. A regra é
        // do ENDEREÇO — o peer `geo` aceita (0,0) de propósito, porque evento natural
        // pode legitimamente ocorrer em alto-mar sobre aquele ponto.
        if (coordenada.isPresent()
                && coordenada.get().latitude() == 0 && coordenada.get().longitude() == 0) {
            throw new EnderecoInvalidoException("coordenada",
                    "(0,0) e o null island, no Golfo da Guine — coordenada ausente e AUSENTE");
        }
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new EnderecoInvalidoException(campo, "vazio");
        }
        return valor.trim();
    }

    /**
     * Este endereço participa do alerta de proximidade?
     *
     * <p>É a pergunta que a tela precisa responder <b>em voz alta</b>: endereço sem
     * coordenada é salvo normalmente, mas nunca gera alerta — e quem cadastrou tem de
     * saber disso, senão vai confiar num aviso que não vai chegar.</p>
     */
    public boolean participaDoAlertaDeProximidade() {
        return coordenada.isPresent();
    }

    /** O mesmo endereço, agora com a coordenada que a geocodificação encontrou. */
    public Endereco comCoordenada(Coordenada nova) {
        return new Endereco(id, cep, numero, logradouro, bairro, localidade, uf, complemento,
                Optional.ofNullable(nova), criadoEm);
    }

    /** Uma linha, para consulta de geocodificação e para conferência humana. */
    public String comoTextoParaBusca() {
        StringBuilder sb = new StringBuilder(logradouro);
        if (numero != null) {
            sb.append(", ").append(numero);
        }
        if (bairro != null && !bairro.isBlank()) {
            sb.append(", ").append(bairro);
        }
        return sb.append(", ").append(localidade).append(" - ").append(uf)
                .append(", ").append(cep.formatado()).toString();
    }
}
