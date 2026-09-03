package org.nasa.painel;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.presentation.web.Icones;
import org.nasa.painel.presentation.web.DocumentacaoCatalogo;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do catálogo da documentação — que ele é coerente e que a página monta.
 *
 * <p><b>O QUE ORIGINOU ISTO.</b> {@link Icones#svg} devolve um ícone genérico para nome
 * desconhecido, <b>de propósito</b>: um erro de digitação não deve derrubar a página. O
 * efeito colateral é que o erro de digitação <b>não aparece</b> — a seção fica com um ícone
 * sem sentido, a tela responde 200, e ninguém percebe. É o mesmo motivo por que
 * {@code CategoriasDeDesastreTest} confere os ícones das treze categorias.</p>
 *
 * <p><b>E há um segundo defeito silencioso, mais caro:</b> um documento declarado cujo
 * arquivo não existe no disco. A página fica com um item a menos, e uma documentação com
 * catorze itens é indistinguível de uma com quinze para quem não conta.</p>
 */
@QuarkusTest
@DisplayName("catalogo da documentacao — icones existem, arquivos existem, secoes fecham")
class CatalogoDaDocumentacaoTest {

    @Inject
    DocumentacaoCatalogo catalogo;

    @Test
    @DisplayName("todo icone pedido pelo catalogo EXISTE")
    void todoIconeExiste() {
        // Sem esta guarda, `{#icone d.icone /}` com nome errado desenha o icone
        // "desconhecido" e a tela responde 200 — o defeito fica invisivel.
        for (var secao : catalogo.getSecoes()) {
            assertTrue(Icones.existe(secao.icone()),
                    "a secao '" + secao.titulo() + "' pede o icone '" + secao.icone()
                            + "', que NAO existe — a tela vai desenhar o generico e "
                            + "responder 200, e ninguem vai notar");
        }
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(Icones.existe(doc.icone()),
                    "o documento '" + doc.titulo() + "' pede o icone '" + doc.icone()
                            + "', que NAO existe");
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o teste de icone sabe REPROVAR")
    void oControleDeIconeReprova() {
        // Sem este caso, `Icones.existe` podendo devolver `true` para tudo faria o teste
        // acima passar sempre — e ele nao provaria nada.
        assertFalse(Icones.existe("icone-que-nao-existe-de-proposito"),
                "Icones.existe aprovou um nome inventado: o teste acima nao mede nada");
        assertFalse(Icones.existe(null));
    }

    @Test
    @DisplayName("todo documento declarado TEM arquivo no disco")
    void todoDocumentoTemArquivo() {
        // Documento declarado e ausente e RELATADO em WARN pelo catalogo — mas WARN em
        // log de producao nao e visto. Aqui ele reprova a compilacao.
        var ausentes = catalogo.declaradosSemArquivo();
        assertTrue(ausentes.isEmpty(),
                "estes documentos estao no catalogo e NAO existem no disco: " + ausentes
                        + " — a pagina fica com itens a menos, e isso e indistinguivel "
                        + "de uma pagina correta");
    }

    @Test
    @DisplayName("todo documento aponta para uma secao QUE EXISTE")
    void todaSecaoDeclaradaExiste() {
        // A tela do documento usa a secao para a trilha e para a cor. Secao orfa vira
        // excecao em tempo de pedido — que e tarde: o visitante ve a pagina de erro.
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(catalogo.secaoDe(doc).isPresent(),
                    "o documento '" + doc.slug() + "' aponta para a secao '" + doc.secao()
                            + "', que nao esta no catalogo de secoes");
        }
    }

    @Test
    @DisplayName("nenhum slug e nenhum arquivo REPETE")
    void nadaRepete() {
        // Slug repetido faz `porSlug` devolver sempre o primeiro, e o segundo documento
        // fica inalcancavel — com link no menu, que e o pior dos casos: parece navegavel.
        Set<String> slugs = new HashSet<>();
        Set<String> arquivos = new HashSet<>();
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(slugs.add(doc.slug()), "slug repetido: " + doc.slug());
            assertTrue(arquivos.add(doc.arquivo()), "arquivo repetido: " + doc.arquivo());
        }
        Set<String> secoes = new HashSet<>();
        for (var s : catalogo.getSecoes()) {
            assertTrue(secoes.add(s.slug()), "slug de secao repetido: " + s.slug());
        }
    }

    @Test
    @DisplayName("nenhum slug, titulo ou icone vem em BRANCO")
    void nadaVemEmBranco() {
        // ESTE TESTE SUBSTITUI UMA VERIFICACAO EM TEMPO DE EXECUCAO.
        //
        // O record `Secao` lancava `IllegalArgumentException` para slug em branco, e a
        // catraca de excecao especifica reprovou — falha sem nome proprio e falha sem
        // causa-raiz. Criar uma excecao nomeada resolveria a catraca e nao seria o certo:
        // o catalogo e feito de literais, entao o defeito nasce ao ESCREVER, e o lugar de
        // pega-lo e aqui, na build — nao no ar, para um visitante, numa tela de erro.
        for (var s : catalogo.getSecoes()) {
            assertFalse(s.slug().isBlank(), "secao com slug em branco: " + s.titulo());
            assertFalse(s.titulo().isBlank(), "secao '" + s.slug() + "' sem titulo");
            assertFalse(s.icone().isBlank(), "secao '" + s.slug() + "' sem icone");
        }
        for (var d : DocumentacaoCatalogo.DOCUMENTOS) {
            assertFalse(d.slug().isBlank(), "documento com slug em branco");
            assertFalse(d.arquivo().isBlank(), "'" + d.slug() + "' sem arquivo");
            assertFalse(d.titulo().isBlank(), "'" + d.slug() + "' sem titulo");
            assertFalse(d.descricao().isBlank(), "'" + d.slug() + "' sem descricao");
            assertFalse(d.icone().isBlank(), "'" + d.slug() + "' sem icone");
        }
    }

    @Test
    @DisplayName("nenhuma secao fica VAZIA no indice")
    void nenhumaSecaoVazia() {
        // Secao sem documento desenha um cabecalho colorido, uma linha e nada embaixo.
        // Acontece de verdade: foi o que sobrou quando a fatia `inscrito` saiu.
        for (var s : catalogo.getSecoes()) {
            assertFalse(catalogo.daSecao(s.slug()).isEmpty(),
                    "a secao '" + s.titulo() + "' nao tem nenhum documento — o indice "
                            + "vai desenhar um cabecalho vazio");
        }
    }

    @Test
    @DisplayName("o tempo de leitura e MEDIDO, e nunca zero para documento que existe")
    void oTempoDeLeituraEhMedido() {
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            int min = catalogo.minutosDe(doc.slug());
            assertTrue(min > 0,
                    "'" + doc.slug() + "' devolveu " + min + " minuto(s): ou o arquivo "
                            + "nao foi lido, ou a contagem esta quebrada");
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o tempo de leitura VARIA entre documentos")
    void oTempoVaria() {
        // ESTA E A GUARDA QUE IMPORTA. Uma contagem quebrada que devolvesse 1 para tudo
        // passaria no teste acima — e o numero na tela seria decorativo, nao medido.
        var distintos = new HashSet<Integer>();
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            distintos.add(catalogo.minutosDe(doc.slug()));
        }
        assertTrue(distintos.size() > 1,
                "todos os " + DocumentacaoCatalogo.DOCUMENTOS.size() + " documentos deram "
                        + distintos + " minuto(s): a contagem nao esta medindo o arquivo, "
                        + "esta devolvendo constante");
    }

    @Test
    @DisplayName("slug de documento nao contem caminho — travessia e impossivel")
    void slugNaoTemCaminho() {
        // O slug vai para a URL e o nome do arquivo vem do catalogo. A trava de travessia
        // esta no `markdownDe`; esta guarda cuida do outro lado, o dado declarado.
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            for (String proibido : new String[] { "/", "\\", "..", ":" }) {
                assertFalse(doc.slug().contains(proibido),
                        "o slug '" + doc.slug() + "' contem '" + proibido + "'");
                assertFalse(doc.arquivo().contains(proibido),
                        "o arquivo '" + doc.arquivo() + "' contem '" + proibido + "'");
            }
        }
    }

    @Test
    @DisplayName("a soma das secoes e o total — nenhum documento fica fora do indice")
    void aSomaFecha() {
        // Um documento cuja secao nao aparece no menu lateral existe, responde 200 e e
        // INALCANCAVEL pela navegacao. Este teste e o que fecha a conta.
        int somados = catalogo.getSecoes().stream()
                .mapToInt(s -> catalogo.daSecao(s.slug()).size())
                .sum();
        assertEquals(DocumentacaoCatalogo.DOCUMENTOS.size(), somados,
                "ha documento que nao aparece em nenhuma secao do menu");
    }
}
