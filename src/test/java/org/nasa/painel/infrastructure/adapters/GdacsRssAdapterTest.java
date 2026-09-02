package org.nasa.painel.infrastructure.adapters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.painel.domain.NivelDeAlerta;
import org.nasa.painel.domain.exceptions.NoticiarioIndisponivelException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da leitura do noticiário GDACS — e da trava contra XXE.
 *
 * <p><b>PROPÓSITO.</b> Dois riscos bem diferentes convivem neste adaptador. Um é de
 * apresentação: ordenar errado esconde o evento vermelho atrás de dez verdes. O outro é de
 * <b>segurança</b>: este código lê XML de origem externa, e um parser de XML em Java sem
 * configuração é a porta aberta mais clássica que existe.</p>
 *
 * <p>O XML abaixo tem a estrutura <b>real</b> do feed, medida em 02/09/2026 contra
 * {@code gdacs.org/xml/rss.xml} — 1 MB e 348 itens no feed completo.</p>
 */
@DisplayName("noticiario GDACS — ordem por gravidade, e a trava contra XXE")
class GdacsRssAdapterTest {

    /** Estrutura REAL do feed, com três itens de níveis diferentes. */
    private static final String FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:gdacs="http://www.gdacs.org"
                 xmlns:geo="http://www.w3.org/2003/01/geo/wgs84_pos#">
              <channel>
                <item>
                  <title>Green earthquake (Magnitude 5.9M, Depth:10km) in Southern East Pacific Rise</title>
                  <link>https://www.gdacs.org/report.aspx?eventtype=EQ&amp;eventid=1563166</link>
                  <pubDate>Wed, 02 Sep 2026 12:55:32 GMT</pubDate>
                  <gdacs:eventtype>EQ</gdacs:eventtype>
                  <gdacs:alertlevel>Green</gdacs:alertlevel>
                  <gdacs:country>Southern East Pacific Rise</gdacs:country>
                  <gdacs:severity>Magnitude 5.9M, Depth:10km</gdacs:severity>
                  <geo:lat>-34.9815</geo:lat>
                  <geo:long>-109.0259</geo:long>
                </item>
                <item>
                  <title>Red tropical cyclone MARIE-26</title>
                  <link>https://www.gdacs.org/report.aspx?eventtype=TC&amp;eventid=1000999</link>
                  <pubDate>Tue, 01 Sep 2026 06:00:00 GMT</pubDate>
                  <gdacs:eventtype>TC</gdacs:eventtype>
                  <gdacs:alertlevel>Red</gdacs:alertlevel>
                  <gdacs:country>Mexico</gdacs:country>
                  <gdacs:severity>Tropical Storm</gdacs:severity>
                  <geo:lat>16.8</geo:lat>
                  <geo:long>-111.3</geo:long>
                </item>
                <item>
                  <title>Orange flood in Brazil</title>
                  <link>https://www.gdacs.org/report.aspx?eventtype=FL&amp;eventid=1000888</link>
                  <pubDate>Wed, 02 Sep 2026 08:00:00 GMT</pubDate>
                  <gdacs:eventtype>FL</gdacs:eventtype>
                  <gdacs:alertlevel>Orange</gdacs:alertlevel>
                  <gdacs:country>Brazil</gdacs:country>
                  <gdacs:severity>Moderate</gdacs:severity>
                </item>
              </channel>
            </rss>""";

    private static GdacsRssAdapter adaptador() {
        var a = new GdacsRssAdapter();
        // Relogio congelado: o teste nao depende do relogio de parede, e a validade do
        // cache passa a ser provavel sem esperar dez minutos.
        a.relogio = () -> java.time.Instant.parse("2026-09-02T12:00:00Z");
        return a;
    }

    @Test
    @DisplayName("BOM: o feed REAL comeca com EF BB BF e mesmo assim tem de ser lido")
    void bomNoInicioNaoQuebraALeitura() {
        // DEFEITO REAL medido em 02/09/2026: o feed do GDACS comeca com BOM de UTF-8. O
        // Java o decodifica como U+FEFF no inicio da String, e o parser recusa com "O
        // conteudo nao e permitido no prologo" — mensagem que nao menciona BOM e manda
        // procurar erro de sintaxe num XML valido.
        //
        // Este caso existe porque o fixture acima, escrito a mao, era MAIS LIMPO QUE A
        // REALIDADE: ele passava enquanto a home mostrava "noticiario indisponivel".
        String comBom = "﻿" + FEED;
        var noticias = adaptador().interpretar(comBom);
        assertEquals(3, noticias.size(), "o BOM derrubou a leitura de um feed valido");
    }

    @Test
    @DisplayName("ordena por GRAVIDADE, nao por data — senao o vermelho fica escondido")
    void ordenaPorGravidade() {
        // O item VERMELHO e o MAIS ANTIGO do feed de propósito: por data ele seria o
        // ultimo, e num carrossel de 348 itens ninguem chegaria nele.
        var noticias = adaptador().interpretar(FEED);

        assertEquals(3, noticias.size());
        assertEquals(NivelDeAlerta.VERMELHO, noticias.get(0).nivel(),
                "o vermelho tem de vir primeiro mesmo sendo o mais antigo");
        assertEquals(NivelDeAlerta.LARANJA, noticias.get(1).nivel());
        assertEquals(NivelDeAlerta.VERDE, noticias.get(2).nivel());
    }

    @Test
    @DisplayName("le tipo, nivel, pais, severidade e coordenada do feed real")
    void leOsCamposDoFeed() {
        var vermelho = adaptador().interpretar(FEED).get(0);

        assertEquals("TC", vermelho.tipoEvento());
        assertEquals("Ciclone tropical", vermelho.tipoPorExtenso(),
                "'TC' nao diz nada a quem chega na home pela primeira vez");
        assertEquals("Mexico", vermelho.pais());
        assertEquals("Tropical Storm", vermelho.severidade());
        assertTrue(vermelho.temPosicao());
        assertEquals(16.8, vermelho.coordenada().latitude(), 0.001);
        assertEquals("2026-09-01T06:00:00Z", vermelho.publicadaEm().toString(),
                "pubDate e RFC-1123 e tem de virar instante UTC");
        System.out.println("[NOTICIA] " + vermelho.titulo() + " | " + vermelho.tipoPorExtenso());
    }

    @Test
    @DisplayName("sem coordenada e AUSENCIA — nunca (0,0) no meio do Atlantico")
    void semCoordenadaEhAusencia() {
        var enchente = adaptador().interpretar(FEED).get(1);
        assertFalse(enchente.temPosicao(), "o item nao trazia geo:lat/geo:long");
        assertNull(enchente.coordenada());
    }

    @Test
    @DisplayName("nivel desconhecido NAO vira verde — pintar de 'tudo bem' esconde o grave")
    void nivelDesconhecidoNaoViraVerde() {
        // O GDACS pode acrescentar um nivel novo sem avisar ninguem.
        assertEquals(NivelDeAlerta.DESCONHECIDO, NivelDeAlerta.de("Purple"));
        assertEquals(NivelDeAlerta.DESCONHECIDO, NivelDeAlerta.de(null));
        assertEquals(NivelDeAlerta.DESCONHECIDO, NivelDeAlerta.de(""));
        assertEquals(0, NivelDeAlerta.DESCONHECIDO.gravidade(),
                "desconhecido nao pode competir com verde na ordenacao");
    }

    @Test
    @DisplayName("item sem link e PULADO — texto que nao leva a lugar nenhum parece defeito")
    void itemSemLinkEhPulado() {
        String comItemTorto = FEED.replace(
                "<link>https://www.gdacs.org/report.aspx?eventtype=FL&amp;eventid=1000888</link>", "");
        var noticias = adaptador().interpretar(comItemTorto);
        assertEquals(2, noticias.size(), "o item sem link sai, os outros dois ficam");
    }

    // ============================================================== SEGURANÇA

    @Test
    @DisplayName("CONTROLE POSITIVO DE XXE: feed com DOCTYPE e RECUSADO")
    void xxeEhRecusado() {
        // Este e o teste que prova a trava de seguranca. Sem `disallow-doctype-decl`, o
        // parser expandiria a entidade e o conteudo do arquivo entraria na resposta — ou,
        // com uma entidade remota, o servidor faria a requisicao que o atacante mandou.
        String hostil = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <rss version="2.0"><channel><item>
                  <title>&xxe;</title>
                  <link>http://exemplo.com</link>
                </item></channel></rss>""";

        var erro = assertThrows(NoticiarioIndisponivelException.class,
                () -> adaptador().interpretar(hostil));

        System.out.println("[NOTICIA] XXE recusado: " + erro.linhaDeLog());
        assertFalse(erro.getMessage().contains("root:"),
                "se aparecer conteudo de arquivo aqui, a entidade foi expandida");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO DE XXE: entidade REMOTA tambem e recusada")
    void xxeRemotoEhRecusado() {
        // A variante que faz o SERVIDOR alcancar a rede interna — varredura de portas
        // usando a nossa maquina como ponto de partida.
        String hostil = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [
                  <!ENTITY remoto SYSTEM "http://169.254.169.254/latest/meta-data/">
                ]>
                <rss version="2.0"><channel><item>
                  <title>&remoto;</title>
                  <link>http://exemplo.com</link>
                </item></channel></rss>""";

        assertThrows(NoticiarioIndisponivelException.class,
                () -> adaptador().interpretar(hostil));
    }

    @Test
    @DisplayName("XML que nao e XML vira excecao PROPRIA, nao NullPointerException")
    void xmlIlegivelViraExcecaoPropria() {
        assertThrows(NoticiarioIndisponivelException.class,
                () -> adaptador().interpretar("isto nao e xml"));
        assertThrows(NoticiarioIndisponivelException.class,
                () -> adaptador().interpretar(""));
    }
}
