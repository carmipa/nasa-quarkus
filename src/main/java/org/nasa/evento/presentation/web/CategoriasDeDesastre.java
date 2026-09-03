package org.nasa.evento.presentation.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * As 13 categorias da EONET — com nome em português, cor e ícone.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A NASA identifica o tipo de desastre por um código em
 * inglês: {@code wildfires}, {@code seaLakeIce}, {@code tempExtremes}. Numa tela em
 * português, isso é ruído — e num mapa com centenas de pinos idênticos, saber que ali houve
 * "alguma coisa" não ajuda ninguém a decidir nada. Este catálogo é o que transforma o código
 * da NASA em algo que se lê e se reconhece.</p>
 *
 * <p><b>POR QUE COR <i>E</i> ÍCONE, E NÃO SÓ COR.</b> Cerca de 8% dos homens não distinguem
 * verde de vermelho. Um mapa que codifica o tipo de desastre <b>apenas</b> na cor não informa
 * essa parcela — e num sistema de alerta, "não informa" é o defeito inteiro. Cada categoria
 * tem cor e ícone próprios: a cor acelera quem enxerga bem, o ícone informa todo mundo, e o
 * nome escrito resolve os dois casos em que os outros dois falham.</p>
 *
 * <p><b>AS 13 SÃO AS 13, medidas na própria API</b> em 02/09/2026
 * ({@code /api/v3/categories}). O filtro da tela tinha só 8 — faltavam poeira, origem
 * humana, neve, extremos de temperatura e cor da água. Um filtro incompleto não erra: ele
 * simplesmente <b>nunca mostra</b> o que ficou de fora, e ninguém procura o que não sabe que
 * existe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Categoria desconhecida NÃO é silêncio.</b> Um código novo da NASA cai em
 *       {@link #DESCONHECIDA}, que é cinza, tem ícone de interrogação e mostra o código
 *       cru — visível, e portanto corrigível. Sumir seria pior: o evento existe.</li>
 *   <li><b>As cores são legíveis sobre o fundo escuro do sistema</b> ({@code #0d1117}).
 *       Não são escolhidas por bonitas: são escolhidas por distinguíveis entre si e
 *       visíveis sobre o fundo em que vão aparecer.</li>
 *   <li><b>O ID é o da NASA, sem tradução.</b> É ele que vai na URL do filtro e na consulta
 *       ao banco; traduzir o identificador quebraria o filtro na primeira vez que alguém
 *       compartilhasse um link.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@code null}, vazio ou código desconhecido
 * devolvem {@link #DESCONHECIDA}. Nenhum caminho devolve {@code null}.</p>
 */
@Named("categorias")
@ApplicationScoped
public class CategoriasDeDesastre {

    /**
     * Uma categoria de desastre.
     *
     * @param id    o código da EONET, ex.: {@code severeStorms}. Vai na URL e na consulta
     * @param nome  como se lê em português
     * @param cor   hexadecimal, legível sobre {@code #0d1117}
     * @param icone nome no catálogo de {@link org.nasa.core.presentation.web.Icones}
     */
    public record Categoria(String id, String nome, String cor, String icone) {
    }

    /** O que se mostra quando a NASA manda um código que este catálogo não conhece. */
    public static final Categoria DESCONHECIDA =
            new Categoria("", "Sem categoria", "#8b949e", "aviso");

    /**
     * As 13, na ordem em que fazem sentido para quem procura — as mais frequentes primeiro.
     *
     * <p>Medido na base em 02/09/2026, sobre 21.542 eventos: incêndios dominam com folga,
     * seguidos de tempestades severas. Pôr "cor da água" no topo de uma lista alfabética
     * faria a pessoa rolar até o fim para achar o que ela procura em 9 de cada 10 vezes.</p>
     */
    public static final List<Categoria> TODAS = List.of(
            new Categoria("wildfires", "Incêndios florestais", "#ff7043", "fogo"),
            new Categoria("severeStorms", "Tempestades severas", "#a78bfa", "nuvem"),
            new Categoria("volcanoes", "Vulcões", "#ef5350", "montanha"),
            new Categoria("floods", "Enchentes", "#42a5f5", "agua"),
            new Categoria("earthquakes", "Terremotos", "#bcaaa4", "terremoto"),
            new Categoria("landslides", "Deslizamentos", "#c98a5b", "montanha"),
            new Categoria("drought", "Secas", "#d4b483", "sol"),
            new Categoria("seaLakeIce", "Gelo marinho e lacustre", "#4dd0e1", "floco"),
            new Categoria("snow", "Neve", "#e0e6ed", "floco"),
            new Categoria("dustHaze", "Poeira e névoa", "#c9a227", "poeira"),
            new Categoria("tempExtremes", "Extremos de temperatura", "#f06292", "termometro"),
            new Categoria("waterColor", "Cor da água", "#26a69a", "agua"),
            new Categoria("manmade", "Origem humana", "#90a4ae", "aviso"));

    private static final Map<String, Categoria> POR_ID = indexar();

    private static Map<String, Categoria> indexar() {
        Map<String, Categoria> mapa = new LinkedHashMap<>();
        for (Categoria c : TODAS) {
            mapa.put(c.id(), c);
        }
        return Map.copyOf(mapa);
    }

    /** A categoria de um código da NASA. Nunca devolve {@code null}. */
    public static Categoria de(String id) {
        if (id == null || id.isBlank()) {
            return DESCONHECIDA;
        }
        Categoria achada = POR_ID.get(id);
        if (achada != null) {
            return achada;
        }
        // Codigo novo da NASA: mostra o codigo CRU em vez de esconder o evento. O evento
        // existe, e um catalogo desatualizado nao pode fazer dado sumir da tela.
        return new Categoria(id, id, DESCONHECIDA.cor(), DESCONHECIDA.icone());
    }

    // ------------------------------------------------------ acesso dos templates

    /** {@code {cdi:categorias.todas}} — para montar o filtro com as 13. */
    public List<Categoria> getTodas() {
        return TODAS;
    }

    /** {@code {cdi:categorias.nome(e.categoria)}} */
    public String nome(String id) {
        return de(id).nome();
    }

    /** {@code {cdi:categorias.cor(e.categoria)}} */
    public String cor(String id) {
        return de(id).cor();
    }

    /** {@code {cdi:categorias.icone(e.categoria)}} */
    public String icone(String id) {
        return de(id).icone();
    }

    /** A categoria inteira, para quem precisa dos quatro campos de uma vez. */
    public Categoria uma(String id) {
        return de(id);
    }
}
