package org.nasa.evento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.presentation.web.Icones;
import org.nasa.evento.presentation.web.CategoriasDeDesastre;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do catálogo de categorias — que ele cobre as 13, e que cada uma é distinguível.
 *
 * <p><b>PROPÓSITO.</b> O filtro da tela era escrito à mão e tinha <b>8 de 13</b> categorias.
 * Faltavam poeira, origem humana, neve, extremos de temperatura e cor da água. Um filtro
 * incompleto não erra — ele simplesmente <b>nunca mostra</b> o que ficou de fora, e ninguém
 * procura o que não sabe que existe. Esta guarda conta.</p>
 */
@DisplayName("categorias de desastre — as 13, distinguiveis por cor E por icone")
class CategoriasDeDesastreTest {

    /**
     * Os 13 IDs medidos em {@code /api/v3/categories} da EONET em 02/09/2026.
     *
     * <p>Escritos aqui, e não buscados na rede: um teste que consulta a NASA falha quando a
     * internet cai, e falhar por rede é ruído que ensina a ignorar o vermelho. O que este
     * conjunto trava é o catálogo <b>não encolher</b> — se a NASA acrescentar uma categoria,
     * a descoberta é manual, e a lista de fora ainda cai no fallback visível.</p>
     */
    private static final Set<String> IDS_DA_EONET = Set.of(
            "drought", "dustHaze", "earthquakes", "floods", "landslides", "manmade",
            "seaLakeIce", "severeStorms", "snow", "tempExtremes", "volcanoes",
            "waterColor", "wildfires");

    @Test
    @DisplayName("as 13 categorias da EONET estao TODAS no catalogo")
    void asTrezeEstaoTodas() {
        Set<String> noCatalogo = new HashSet<>();
        for (var c : CategoriasDeDesastre.TODAS) {
            noCatalogo.add(c.id());
        }

        Set<String> faltando = new HashSet<>(IDS_DA_EONET);
        faltando.removeAll(noCatalogo);
        assertTrue(faltando.isEmpty(),
                "categorias da EONET AUSENTES do catalogo: " + faltando
                        + " — o filtro nunca vai mostrar eventos desses tipos");

        Set<String> sobrando = new HashSet<>(noCatalogo);
        sobrando.removeAll(IDS_DA_EONET);
        assertTrue(sobrando.isEmpty(),
                "categorias no catalogo que a EONET NAO tem: " + sobrando
                        + " — filtro que nunca devolve nada");

        assertEquals(13, CategoriasDeDesastre.TODAS.size());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: cada categoria tem cor PROPRIA — nenhuma repetida")
    void nenhumaCorSeRepete() {
        // Duas categorias da mesma cor sao indistinguiveis no mapa, e o mapa passa a
        // mentir: o pino diz "e um destes dois tipos" enquanto parece dizer qual.
        Set<String> cores = new HashSet<>();
        for (var c : CategoriasDeDesastre.TODAS) {
            assertTrue(cores.add(c.cor()),
                    "a cor " + c.cor() + " se repete — '" + c.nome()
                            + "' fica indistinguivel de outra categoria no mapa");
        }
        assertEquals(13, cores.size());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: toda cor e #rrggbb valido — o mapa valida e descartaria")
    void todaCorEhHexadecimalValido() {
        // O `corSegura` do mapa so aceita `#rrggbb` e troca o resto por cinza. Uma cor
        // torta aqui viraria pino cinza SEM ERRO NENHUM — e a categoria perderia a cor
        // em silencio, que e o defeito que este projeto ja pagou tres vezes.
        Pattern hex = Pattern.compile("^#[0-9a-fA-F]{6}$");
        for (var c : CategoriasDeDesastre.TODAS) {
            assertTrue(hex.matcher(c.cor()).matches(),
                    "'" + c.nome() + "' tem cor invalida (" + c.cor()
                            + "): o mapa a descartaria e o pino ficaria cinza, sem erro");
        }
        assertTrue(hex.matcher(CategoriasDeDesastre.DESCONHECIDA.cor()).matches());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: todo icone declarado EXISTE no catalogo de icones")
    void todoIconeExiste() {
        // Icone inexistente vira o triangulo de aviso — visivel, mas errado: a categoria
        // perderia a marca que a distingue para quem nao enxerga a cor.
        for (var c : CategoriasDeDesastre.TODAS) {
            assertTrue(Icones.existe(c.icone()),
                    "'" + c.nome() + "' pede o icone '" + c.icone()
                            + "', que NAO existe no catalogo de icones");
        }
        assertTrue(Icones.existe(CategoriasDeDesastre.DESCONHECIDA.icone()));
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: os nomes estao em PORTUGUES, nao no codigo da NASA")
    void osNomesEstaoTraduzidos() {
        // Sem este caso, um catalogo que devolvesse o proprio id como nome passaria em
        // todos os testes acima — e a tela continuaria mostrando `seaLakeIce`.
        for (var c : CategoriasDeDesastre.TODAS) {
            assertFalse(c.nome().equals(c.id()),
                    "'" + c.id() + "' nao foi traduzido — a tela mostraria o codigo cru");
        }
        assertEquals("Incêndios florestais", CategoriasDeDesastre.de("wildfires").nome());
        assertEquals("Gelo marinho e lacustre", CategoriasDeDesastre.de("seaLakeIce").nome());
    }

    @Test
    @DisplayName("categoria desconhecida NAO some — mostra o codigo cru, visivel")
    void desconhecidaNaoSome() {
        // Um codigo novo da NASA nao pode fazer o evento sumir da tela: o evento existe,
        // e um catalogo desatualizado nao e motivo para esconder dado.
        var nova = CategoriasDeDesastre.de("meteorStrikes");
        assertEquals("meteorStrikes", nova.id(), "o id tem de sobreviver, para o filtro funcionar");
        assertEquals("meteorStrikes", nova.nome(), "sem traducao, mostra o codigo — nunca vazio");
        assertTrue(Icones.existe(nova.icone()));

        for (String vazio : new String[] { null, "", "   " }) {
            var c = CategoriasDeDesastre.de(vazio);
            assertEquals("Sem categoria", c.nome());
            assertFalse(c.cor().isBlank());
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: a ordem poe as FREQUENTES primeiro")
    void asFrequentesVemPrimeiro() {
        // Medido na base: incendios dominam com folga. Ordem alfabetica poria "cor da
        // agua" no topo e obrigaria rolar ate o fim para achar o que se procura em 9 de
        // cada 10 vezes.
        List<CategoriasDeDesastre.Categoria> todas = CategoriasDeDesastre.TODAS;
        assertEquals("wildfires", todas.get(0).id(),
                "incendios sao a categoria mais frequente e deveriam abrir a lista");
        assertEquals("severeStorms", todas.get(1).id());
    }
}
