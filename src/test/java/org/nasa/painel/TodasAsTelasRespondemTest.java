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

    @jakarta.inject.Inject
    org.nasa.evento.application.ConsultarEventosUseCase consultarEventos;

    /**
     * O limitador por origem.
     *
     * <p><b>Declarado aqui de propósito, e não escondido.</b> Ele é estado global — é essa
     * a função dele —, e por isso a suíte fica dependente de ORDEM: o teste do limitador
     * esgota a cota, e este, rodando depois, tinha as inscrições barradas e reprovava por um
     * motivo que não era defeito.</p>
     *
     * <p>Zerar no {@code @BeforeEach} torna a dependência <b>visível</b>. A alternativa —
     * configurar um limite altíssimo só em teste — esconderia o acoplamento e faria a suíte
     * medir uma configuração que ninguém roda.</p>
     */
    @jakarta.inject.Inject
    org.nasa.core.web.LimiteDeTentativas limitador;

    @org.junit.jupiter.api.BeforeEach
    void zerarOLimitador() {
        limitador.esquecerTudo();
    }

    @jakarta.inject.Inject
    org.nasa.evento.domain.ports.RepositorioDeEventosPort repositorioDeEventos;

    /**
     * Cria eventos COM COORDENADA, para as guardas do mapa julgarem alguma coisa.
     *
     * <p><b>Por que isto existe.</b> A base de teste não tem evento nenhum — eventos vêm da
     * sincronização com a NASA, e teste não fala com a NASA. Sem estes registros, as
     * guardas do mapa passavam <b>por vacuidade</b>: comparavam zero com zero e nunca
     * julgavam nada. Guarda que não pode reprovar é documentação, não guarda.</p>
     *
     * <p>Duas categorias diferentes de propósito: uma só faria o filtro por tipo parecer
     * funcionar mesmo se ele ignorasse o parâmetro.</p>
     */
    private void criarEventosComCoordenada(int quantos) {
        for (int i = 0; i < quantos; i++) {
            repositorioDeEventos.gravarOuAtualizar(
                    org.nasa.evento.domain.EventoNatural.lidoDaNasa(
                            "EONET_MAPA_" + i,
                            "Evento de teste " + i,
                            i % 2 == 0 ? "wildfires" : "volcanoes",
                            java.time.Instant.now().minusSeconds(3600L * i),
                            // Longitudes espalhadas, latitude fixa: coordenada valida em
                            // qualquer i, sem esbarrar nos polos nem no antimeridiano.
                            new org.nasa.geo.domain.Coordenada(-10.0, -180.0 + (i % 360)),
                            "{}", null));
        }
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
    @DisplayName("a tela de INSCRICAO renderiza, e o e-mail repetido e RECUSA, nao erro")
    void telaDeInscricao() {
        deveResponder("/inscricao");
        deveResponder("/inscricao?pagina=1");

        // O CLIQUE DUPLO. E o erro de boa-fe mais comum que existe num formulario:
        // clicar de novo porque a pagina demorou. A segunda inscricao com o mesmo e-mail
        // tem de responder 200 com "voce ja esta inscrito" — nao 500, e nao uma segunda
        // linha no banco que faria a pessoa receber cada alerta em dobro.
        String email = "duplo" + System.nanoTime() + "@exemplo.test";
        String[] corpos = new String[2];
        for (int i = 0; i < 2; i++) {
            var r = given().contentType(ContentType.URLENC)
                    .formParam("nome", "Teste Duplo")
                    .formParam("email", email)
                    .formParam("cep", "01310100")
                    .when().post("/inscricao");
            assertEquals(200, r.statusCode(),
                    "a inscricao numero " + (i + 1) + " respondeu " + r.statusCode());
            corpos[i] = r.asString();
        }

        // A ASERCAO E SOBRE O COMPORTAMENTO, nao sobre a lista.
        //
        // A primeira versao procurava o e-mail na primeira pagina da lista — e reprovou
        // por um motivo que nao era defeito: a lista e paginada em 20, e outros testes ja
        // tinham enchido a pagina. Um teste que depende do que outro teste deixou nao
        // prova nada sobre o que ele diz medir.
        assertTrue(corpos[0].contains("Pronto,"),
                "a PRIMEIRA inscricao nao foi aceita");
        assertTrue(corpos[1].contains("já está inscrito"),
                "a SEGUNDA inscricao com o mesmo e-mail nao foi reconhecida como repetida — "
                        + "o clique duplo cria duas inscricoes e a pessoa recebe tudo em dobro");
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
        for (String rota : new String[] { "/", "/contato", "/inscricao", "/desastres", "/desastres/mapa",
                "/desastres/estatisticas", "/alertas", }) {
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
                "/documentacao" }) {
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
    @DisplayName("CONTROLE POSITIVO: o mapa manda TODOS os eventos, nao so a primeira pagina")
    void oMapaMandaTodosOsEventos() {
        // A lista abaixo do mapa tem DOIS papeis: e a fonte dos pinos (o script le
        // `data-latitude` de cada item) e e a versao legivel para quem esta sem
        // JavaScript. A paginacao dela e feita no NAVEGADOR justamente por isso.
        //
        // Se alguem paginar no servidor para "arrumar a lista", o mapa passa a desenhar
        // 50 pinos em vez de 500 — em silencio, e o mapa e o produto da tela. Este teste
        // e o que reprova essa troca.
        // Sem estes, o teste comparava zero com zero e nunca julgava nada.
        criarEventosComCoordenada(60);

        String corpo = corpoDe("/desastres/mapa");
        long naPagina = corpo.lines().filter(l -> l.contains("data-latitude=")).count();

        // A ASERCAO E CONTRA O QUE O SERVIDOR TEM, nao contra um numero fixo.
        //
        // A primeira versao deste teste exigia "mais de 50" e reprovou — corretamente:
        // o banco de teste tem punhado de eventos, nao o volume de producao. Numero fixo
        // num teste e uma suposicao sobre o ambiente disfarcada de asercao.
        long esperado = Math.min(
                consultarEventos.paraOMapa(java.util.List.of(),
                        org.nasa.evento.application.ConsultarEventosUseCase.MAXIMO_NO_MAPA).size(),
                org.nasa.evento.application.ConsultarEventosUseCase.MAXIMO_NO_MAPA);

        assertEquals(esperado, naPagina,
                "o servidor mandou " + naPagina + " evento(s) com coordenada, mas tem "
                        + esperado + ". Se alguem paginou a lista NO SERVIDOR para encurtar"
                        + " a pagina, o mapa passou a desenhar so a primeira pagina de pinos"
                        + " — em silencio. A paginacao desta lista e do NAVEGADOR.");

        // CONTROLE DO CONTROLE: com zero evento a asercao acima compara 0 com 0 e passa
        // sem julgar nada. Este piso e o que a torna exigivel — e ele passou de 50 de
        // proposito, que e o tamanho da pagina da lista: abaixo disso a paginacao do
        // navegador nem seria acionada, e o teste nao estaria no cenario que importa.
        assertTrue(esperado > 50,
                "so " + esperado + " evento(s) com coordenada: a asercao acima passa por"
                        + " vacuidade e a guarda nao julga nada");
    }

    @Test
    @DisplayName("as telas trazem DICAS explicando os campos")
    void asTelasTrazemDicas() {
        // A dica explica o que cada campo faz. Sem ela a tela funciona e ninguem
        // entende — que e o estado em que o sistema parece simples e nao e.
        for (String rota : new String[] { "/desastres", "/desastres/historico",
                "/desastres/estatisticas", "/alertas" }) {
            String corpo = corpoDe(rota);
            assertTrue(corpo.contains("data-dica="),
                    "nenhuma dica de campo em " + rota);
        }
    }

    /** As telas com moldura — as que um visitante abre pela URL. */
    private static final String[] ROTAS_COM_MOLDURA = {
            "/", "/contato", "/documentacao", "/inscricao", "/telemetria",
            "/desastres", "/desastres/mapa", "/desastres/estatisticas", "/desastres/historico",
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
