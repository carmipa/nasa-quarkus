package org.nasa.painel;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Toda tela do sistema RENDERIZA — inclusive os ramos que só aparecem com dados.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Template não é código compilado. Uma expressão errada
 * dentro de um {@code {#if}} fica <b>invisível</b> até aquele ramo acontecer — e aí derruba
 * a página com 500 na cara de quem estava usando.</p>
 *
 * <p><b>O DEFEITO QUE ORIGINOU ESTE ARQUIVO</b> (02/09/2026). Escrevi
 * {@code {termo.urlEncoded}} na paginação da lista de clientes, supondo uma extensão que o
 * Qute <b>não tem</b>. A expressão vive dentro do bloco de paginação, que só é renderizado
 * quando existe página anterior ou próxima. Com quatro clientes na base, o bloco nunca foi
 * desenhado: passou nos testes, passou no uso à mão, e ficou lá esperando o quinto
 * cadastro. Só apareceu quando a lista de desastres, com quarenta eventos, paginou de
 * verdade — e aí eram <b>duas</b> telas quebradas, não uma.</p>
 *
 * <p><b>POR ISSO ESTE TESTE CRIA DADOS ANTES DE OLHAR.</b> Percorrer as rotas com a base
 * vazia provaria apenas que o caminho do "não há nada" funciona — que é o ramo mais fácil
 * e o menos usado. Aqui a base ganha registros suficientes para <b>paginar</b>, e é a
 * paginação que se quer ver desenhada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Toda rota de tela devolve 200 e HTML.</b> Um 500 aqui é template quebrado.</li>
 *   <li><b>A moldura aparece em todas.</b> Se o relógio ou o menu sumirem de uma tela, é
 *       porque alguém deixou de passar pela {@code MolduraDaPagina}.</li>
 *   <li><b>Nenhuma tela vaza rastro de pilha.</b> Página de erro do Quarkus na tela é
 *       informação de infraestrutura para quem não deveria vê-la.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova nomeando a rota e o status — e o corpo
 * da resposta contém a mensagem do Qute, que diz qual expressão e qual linha.</p>
 */
@QuarkusTest
@DisplayName("todas as telas renderizam — inclusive os ramos que so aparecem COM dados")
class TodasAsTelasRespondemTest {

    /**
     * Quantos registros criar antes de olhar.
     *
     * <p>Precisa passar do tamanho de página das listas — que é 20 — para que o bloco de
     * paginação seja <b>realmente</b> desenhado. Foi exatamente esse bloco que escondeu o
     * defeito por um dia inteiro.</p>
     */
    private static final int REGISTROS = 22;

    private static String documentoNovo() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private void criarClientesSuficientesParaPaginar() {
        for (int i = 0; i < REGISTROS; i++) {
            given().contentType(ContentType.JSON)
                    .body("""
                            {"nome":"Paginacao%d","sobrenome":"Teste",
                             "dataNascimento":"1990-01-01","documento":"%s"}"""
                            .formatted(i, documentoNovo()))
                    .when().post("/api/clientes");
        }
    }

    @Test
    @DisplayName("as telas de CLIENTE renderizam, e a PAGINACAO e desenhada de verdade")
    void telasDeClienteComPaginacao() {
        criarClientesSuficientesParaPaginar();

        // A lista inteira, com o bloco de paginacao desenhado — o ramo que escondia o
        // `urlEncoded` inexistente.
        String lista = corpoDe("/clientes/fragmento/lista");
        assertTrue(lista.contains("paginacao") || lista.contains("Próxima"),
                "o bloco de paginacao NAO foi desenhado: este teste nao esta exercitando "
                        + "o ramo que escondeu o defeito. Aumente REGISTROS.");

        // E com TERMO, que e o valor que passa pelo urlEncoded.
        deveResponder("/clientes/fragmento/lista?termo=paginacao");
        deveResponder("/clientes/fragmento/lista?termo=nome%20com%20espaco%20e%20acento");
        deveResponder("/clientes/fragmento/lista?termo=paginacao&pagina=1");
    }

    @Test
    @DisplayName("as telas de DESASTRE renderizam, com e sem filtro")
    void telasDeDesastre() {
        Map<String, String> rotas = new LinkedHashMap<>();
        rotas.put("painel", "/desastres");
        rotas.put("mapa", "/desastres/mapa");
        rotas.put("estatisticas", "/desastres/estatisticas");
        rotas.put("estatisticas com janela", "/desastres/estatisticas?dias=365");
        rotas.put("mapa filtrado por um tipo", "/desastres/mapa?categoria=volcanoes");
        rotas.put("mapa filtrado por dois tipos",
                "/desastres/mapa?categoria=volcanoes&categoria=floods");
        // Categoria que NAO existe: a borda descarta e o mapa se comporta como sem filtro,
        // em vez de consultar o banco por um valor que nunca casa.
        rotas.put("mapa com categoria inventada", "/desastres/mapa?categoria=xpto");
        rotas.put("mapa com categoria vazia", "/desastres/mapa?categoria=");
        rotas.put("historico por ano", "/desastres/historico");
        rotas.put("detalhe de um ano", "/desastres/historico/2026");
        // Ano SEM eventos gravados: o ramo em que `barras` vem null, e o template
        // precisa sobreviver a isso. E o mesmo defeito de `criado` e `salvo`.
        rotas.put("ano sem eventos", "/desastres/historico/2015");
        rotas.put("lista", "/desastres/fragmento/lista");
        rotas.put("lista com categoria", "/desastres/fragmento/lista?categoria=wildfires");
        rotas.put("lista pagina 1", "/desastres/fragmento/lista?categoria=&pagina=1");
        rotas.put("proximos sem procurar", "/desastres/fragmento/proximos");
        rotas.put("proximos com busca",
                "/desastres/fragmento/proximos?procurou=true&latitude=-23.55"
                        + "&longitude=-46.63&raioKm=500&dias=60");
        // Raio invalido: o ramo de ERRO da tela, que so aparece quando algo falha.
        rotas.put("proximos com raio invalido",
                "/desastres/fragmento/proximos?procurou=true&latitude=-23.55"
                        + "&longitude=-46.63&raioKm=-1");

        rotas.forEach((nome, rota) -> deveResponder(rota));
    }

    @Test
    @DisplayName("as telas de CONTATO renderizam, com e sem filtro de tipo")
    void telasDeContato() {
        deveResponder("/contatos/listar");
        deveResponder("/contatos/cadastrar");
        deveResponder("/contatos/fragmento/lista");
        deveResponder("/contatos/fragmento/lista?tipo=EMERGENCIA");
        deveResponder("/contatos/fragmento/lista?termo=exemplo");
        // O ramo com termo E pagina: e onde o urlEncoded aparece.
        deveResponder("/contatos/fragmento/lista?termo=exemplo&tipo=&pagina=1");
    }

    @Test
    @DisplayName("as telas de ALERTA renderizam, com e sem filtro de situacao")
    void telasDeAlerta() {
        deveResponder("/alertas");
        deveResponder("/alertas/fragmento/lista");
        // Os tres ramos da lista por situacao — cada um com seu texto proprio.
        deveResponder("/alertas/fragmento/lista?situacao=PENDENTE");
        deveResponder("/alertas/fragmento/lista?situacao=ENVIADO");
        deveResponder("/alertas/fragmento/lista?situacao=FALHOU");
        deveResponder("/alertas/fragmento/lista?situacao=ENVIADO&pagina=1");
    }

    @Test
    @DisplayName("as telas de ENDERECO renderizam, e os TRES estados do CEP")
    void telasDeEndereco() {
        deveResponder("/enderecos/listar");
        deveResponder("/enderecos/cadastrar");
        deveResponder("/enderecos/cadastrar?clienteId=1");
        // Os tres estados do fragmento de CEP, cada um com seu texto proprio.
        deveResponder("/enderecos/fragmento/por-cep");                 // nao consultei
        deveResponder("/enderecos/fragmento/por-cep?cep=123");         // incompleto
        deveResponder("/enderecos/fragmento/por-cep?cep=00000000");    // nao existe
    }

    @Test
    @DisplayName("as telas PUBLICAS renderizam, e trazem a moldura inteira")
    void telasPublicas() {
        for (String rota : new String[] { "/", "/contato" }) {
            String corpo = corpoDe(rota);
            // A moldura e o que garante relogio, menu e seletor de idioma. Se ela sumir de
            // uma tela, e porque alguem deixou de passar pela MolduraDaPagina.
            assertTrue(corpo.contains("data-instante-servidor"),
                    "sem o relogio da moldura em " + rota);
            assertTrue(corpo.contains("class=\"menu\""), "sem o menu em " + rota);
            assertTrue(corpo.contains("data-idioma=\"pt\""), "sem o seletor de idioma em " + rota);
        }
    }

    @Test
    @DisplayName("nenhuma tela vaza rastro de pilha")
    void nenhumaTelaVazaRastroDePilha() {
        // Pagina de erro do Quarkus mostra pacote, classe e linha — informacao de
        // infraestrutura para quem nao deveria ve-la.
        for (String rota : new String[] { "/", "/contato", "/desastres", "/desastres/mapa",
                "/desastres/estatisticas", "/clientes/listar", "/clientes/cadastrar",
                "/clientes/buscar", "/alertas", "/contatos/listar",
                "/contatos/cadastrar", "/enderecos/listar",
                "/enderecos/cadastrar" }) {
            String corpo = corpoDe(rota);
            assertTrue(!corpo.contains("org.nasa.") || !corpo.contains("at java."),
                    "rastro de pilha visivel em " + rota);
        }
    }

    // ================================================== EXPRESSAO CRUA NA TELA

    /**
     * Nenhuma tela mostra uma expressão de template <b>como texto</b>.
     *
     * <p><b>O DEFEITO QUE ORIGINOU ESTE TESTE, medido em 02/09/2026.</b> O ícone foi
     * escrito como {@code {'historico'.icone.raw}}, apostando numa
     * {@code @TemplateExtension}. O Qute <b>não reconhece expressão que começa por
     * aspas</b> — e em vez de falhar, ele imprimiu {@code {'historico'.icone.raw}} como
     * texto literal na página. Status <b>200</b>, sem erro em log nenhum, com o
     * código-fonte do template à mostra para o visitante.</p>
     *
     * <p><b>Por que os testes existentes não pegariam.</b> Todos eles conferem
     * {@code statusCode == 200}, e o 200 estava lá. É a lição de novo: <b>200 não prova
     * que a página está certa</b>. Este teste olha o CONTEÚDO.</p>
     */
    @Test
    @DisplayName("CONTROLE POSITIVO: nenhuma tela imprime expressao de template como texto")
    void nenhumaTelaVazaExpressaoDeTemplate() {
        for (String rota : ROTAS_COM_MOLDURA) {
            String corpo = corpoDe(rota);
            for (String vazamento : new String[] { "{#icone", "{cdi:", ".raw}", "{#if ", "{#for " }) {
                assertFalse(corpo.contains(vazamento),
                        "a rota " + rota + " imprimiu '" + vazamento + "' como TEXTO — o Qute"
                                + " nao interpretou a expressao e ela vazou para a pagina");
            }
        }
    }

    @Test
    @DisplayName("os icones sao DESENHADOS, nao escapados nem vazios")
    void osIconesSaoDesenhados() {
        // Tres estados diferentes, e so o primeiro esta certo:
        //   <svg class='icone'   -> desenhou
        //   &lt;svg              -> escapou, e a pagina mostra o codigo do desenho
        //   nada                 -> sumiu em silencio, indistinguivel de layout correto
        for (String rota : new String[] { "/", "/desastres", "/desastres/historico",
                "/clientes/listar", "/documentacao" }) {
            String corpo = corpoDe(rota);
            assertFalse(corpo.contains("&lt;svg"),
                    "o SVG foi ESCAPADO em " + rota + " — falta o `.raw`");
            assertTrue(corpo.contains("<svg class='icone'"),
                    "nenhum icone desenhado em " + rota);
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: icone desconhecido APARECE, nao some")
    void iconeDesconhecidoNaoSomeEmSilencio() {
        // Um nome com erro de digitacao que renderiza nada e indistinguivel de um
        // layout correto — ninguem conta os icones de uma tela. Ele tem de VIRAR ALGO.
        String svg = org.nasa.core.presentation.web.Icones.svg("nome-que-nao-existe");
        assertTrue(svg.contains("<svg"), "icone desconhecido sumiu: " + svg);
        assertTrue(svg.length() > 100, "icone desconhecido veio vazio por dentro: " + svg);
        assertFalse(org.nasa.core.presentation.web.Icones.existe("nome-que-nao-existe"));
        // E o controle do controle: um nome REAL tem de existir, senao o teste acima
        // passaria num catalogo vazio.
        assertTrue(org.nasa.core.presentation.web.Icones.existe("casa"));
        assertTrue(org.nasa.core.presentation.web.Icones.nomes().size() >= 20,
                "o catalogo tem " + org.nasa.core.presentation.web.Icones.nomes().size()
                        + " icones: os testes acima estao julgando quase nada");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o filtro do mapa traz AS 13 categorias, nunca menos")
    void oFiltroDoMapaTrazAsTreze() {
        // O DEFEITO QUE ESTE TESTE TRAVA, medido em 02/09/2026. A primeira versao do
        // filtro listava so as categorias COM evento desenhavel — eram 10. As tres de
        // fora (neve, extremos de temperatura, origem humana) nao estavam vazias: tinham
        // 3, 14 e 5 eventos na base, todos sem coordenada publicada pela NASA.
        //
        // Escondê-las repetia o defeito que este projeto ja tinha corrigido no filtro da
        // lista, e esta escrito na propria documentacao dele: filtro incompleto nao erra,
        // ele simplesmente NUNCA mostra o que ficou de fora. Quem procurasse "neve"
        // concluiria que a NASA nao publica neve.
        String corpo = corpoDe("/desastres/mapa");

        for (var categoria : org.nasa.evento.presentation.web.CategoriasDeDesastre.TODAS) {
            assertTrue(corpo.contains(categoria.nome()),
                    "a categoria '" + categoria.nome() + "' sumiu do filtro do mapa — "
                            + "quem procurar por ela vai concluir que ela nao existe");
        }

        // CONTROLE do controle: 13 caixas de selecao, uma por categoria. Sem contar, o
        // teste acima passaria com o nome aparecendo em qualquer outro lugar da pagina.
        long caixas = corpo.lines()
                .filter(l -> l.contains("name=\"categoria\"") && l.contains("checkbox"))
                .count();
        assertEquals(13, caixas,
                "o filtro do mapa tem " + caixas + " caixas; a EONET tem 13 categorias");
    }

    @Test
    @DisplayName("as telas trazem DICAS explicando os campos")
    void asTelasTrazemDicas() {
        // A dica explica o que cada campo faz. Sem ela a tela funciona e ninguem
        // entende — que e o estado em que o sistema parece simples e nao e.
        for (String rota : new String[] { "/desastres", "/desastres/historico",
                "/desastres/estatisticas", "/clientes/buscar", "/alertas" }) {
            String corpo = corpoDe(rota);
            assertTrue(corpo.contains("data-dica="),
                    "nenhuma dica de campo em " + rota);
        }
    }

    /** As telas com moldura — as que um visitante abre pela URL. */
    private static final String[] ROTAS_COM_MOLDURA = {
            "/", "/contato", "/documentacao",
            "/desastres", "/desastres/mapa", "/desastres/estatisticas", "/desastres/historico",
            "/clientes/listar", "/clientes/cadastrar", "/clientes/buscar",
            "/contatos/listar", "/contatos/cadastrar",
            "/enderecos/listar", "/enderecos/cadastrar",
            "/alertas" };

    // ------------------------------------------------------------------ apoio

    private void deveResponder(String rota) {
        int status = given().when().get(rota).statusCode();
        assertEquals(200, status, "a rota " + rota + " respondeu " + status
                + " — provavelmente template quebrado; o corpo diz a expressao e a linha");
    }

    private String corpoDe(String rota) {
        var r = given().when().get(rota);
        assertEquals(200, r.statusCode(), "a rota " + rota + " respondeu " + r.statusCode()
                + ": " + r.asString().substring(0, Math.min(400, r.asString().length())));
        return r.asString();
    }
}
