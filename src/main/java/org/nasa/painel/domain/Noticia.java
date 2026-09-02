package org.nasa.painel.domain;

import org.nasa.geo.domain.Coordenada;

import java.time.Instant;
import java.util.Optional;

/**
 * Uma notícia de desastre, como o GDACS publica.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que a home mostra em carrossel: o que está
 * acontecendo no mundo agora. Serve a duas coisas — dar contexto a quem chega ao sistema
 * pela primeira vez, e mostrar que os dados são reais e recentes.</p>
 *
 * <p><b>DE ONDE VEM, E POR QUE MUDOU.</b> O legado usava a
 * {@code api.reliefweb.int/v1}. Medido em 02/09/2026, essa API está <b>morta</b>:
 * responde {@code HTTP 410 — "The API version 'v1' has been decommissioned"}. A v2 existe
 * e responde {@code 403} sem um {@code appname} previamente aprovado pela ReliefWeb —
 * inclusive para o appname que o legado usava. Ou seja: <b>o carrossel de notícias do
 * projeto original não funciona mais</b>, e não é por causa da reescrita.</p>
 *
 * <p>No lugar entrou o <b>GDACS</b> (Global Disaster Alert and Coordination System, da ONU
 * e da União Europeia), que responde {@code 200} sem cadastro e é <b>melhor</b> para este
 * sistema por três motivos medidos no feed real:</p>
 * <ul>
 *   <li>é especificamente sobre <b>desastres</b>, não sobre relatórios humanitários em
 *       geral;</li>
 *   <li>traz <b>nível de alerta</b> (verde/laranja/vermelho), então a tela consegue
 *       destacar o que importa em vez de listar 348 itens com o mesmo peso;</li>
 *   <li>traz <b>coordenadas</b>, o que permite cruzar a notícia com a mesma geodésia que o
 *       resto do sistema usa — coisa que a fonte anterior não permitia.</li>
 * </ul>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Título e link são obrigatórios.</b> Notícia sem link é um texto que não leva a
 *       lugar nenhum, e quem clica conclui que a tela está quebrada.</li>
 *   <li><b>Coordenada é opcional e AUSENTE quando falta</b> — nunca {@code (0,0)}. É a
 *       mesma regra do endereço e do evento.</li>
 *   <li><b>O nível desconhecido nunca vira verde.</b> Pintar de "tudo bem" o que não se
 *       entendeu é como um evento grave passa despercebido.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Este tipo não valida além do essencial: uma
 * notícia é conteúdo de vitrine, e recusar o feed inteiro por causa de um campo faltando
 * deixaria a home vazia por um detalhe. O adaptador pula o item torto e conta.</p>
 *
 * @param id          identificador na fonte
 * @param titulo      como o GDACS escreve
 * @param link        para onde vai quem clica
 * @param publicadaEm instante UTC da publicação
 * @param tipoEvento  código do GDACS: EQ, TC, FL, VO, DR, WF
 * @param nivel       verde, laranja, vermelho — ou desconhecido
 * @param pais        região afetada, como a fonte descreve
 * @param severidade  texto livre da fonte, ex.: "Magnitude 5.9M, Depth:10km"
 * @param coordenada  onde aconteceu; ausente quando a fonte não informou
 * @param imagem      mapa do evento gerado pelo GDACS; ausente quando não há
 * @param icone       ícone do tipo + nível; são apenas 9 distintos em 348 itens,
 *                    então o navegador os reaproveita quase sempre
 */
public record Noticia(String id, String titulo, String link, Instant publicadaEm,
                      String tipoEvento, NivelDeAlerta nivel, String pais,
                      String severidade, Coordenada coordenada,
                      String imagem, String icone) {

    public Optional<Coordenada> coordenadaOpcional() {
        return Optional.ofNullable(coordenada);
    }

    /**
     * O nome do tipo de evento por extenso.
     *
     * <p>O GDACS usa siglas de duas letras. "EQ" não diz nada a quem chega à home pela
     * primeira vez, que é justamente o público desta tela.</p>
     */
    public String tipoPorExtenso() {
        if (tipoEvento == null) {
            return "Evento";
        }
        return switch (tipoEvento.strip().toUpperCase()) {
            case "EQ" -> "Terremoto";
            case "TC" -> "Ciclone tropical";
            case "FL" -> "Enchente";
            case "VO" -> "Vulcão";
            case "DR" -> "Seca";
            case "WF" -> "Incêndio florestal";
            case "TS" -> "Tsunami";
            // Sigla nova do GDACS: mostra a sigla em vez de inventar um nome, porque
            // inventar seria afirmar algo que a fonte nao disse.
            default -> tipoEvento.strip().toUpperCase();
        };
    }

    /**
     * A imagem que a tela deve mostrar, ou {@code null}.
     *
     * <p>Prefere o mapa do evento; cai no ícone quando não há mapa. As duas são
     * <b>servidas pelo nosso servidor</b>, e não direto do GDACS — porque aquele host
     * limita vazão (medido: a mesma URL responde 200 isolada e falha em sequência) e
     * porque um {@code <img>} apontando para fora entrega o IP de cada visitante a um
     * terceiro que ninguém escolheu contatar.</p>
     */
    public String imagemPreferida() {
        if (imagem != null && !imagem.isBlank()) {
            return imagem;
        }
        return icone == null || icone.isBlank() ? null : icone;
    }

    public boolean temImagem() {
        return imagemPreferida() != null;
    }

    /** Se esta notícia dá para desenhar no mapa. */
    public boolean temPosicao() {
        return coordenada != null;
    }
}
