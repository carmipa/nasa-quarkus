package org.nasa.cliente.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.nasa.cliente.application.AlterarClienteUseCase;
import org.nasa.cliente.application.CadastrarClienteUseCase;
import org.nasa.cliente.application.ConsultarClientesUseCase;
import org.nasa.cliente.application.ExcluirClienteUseCase;
import org.nasa.cliente.domain.Cliente;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * As telas de cliente — listar, cadastrar, buscar, ver, alterar e excluir.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Reconstrói em HTMX as seis telas que o front legado
 * fazia em Next/React. Cada tela tem <b>pasta própria</b>, com o seu HTML, o seu CSS e o
 * seu JS — nunca um arquivo compartilhado. No legado havia um {@code globals.css} de 801
 * linhas para as treze telas: mexer no espaçamento de uma arriscava as outras doze, e não
 * havia como saber qual regra pertencia a quem.</p>
 *
 * <p><b>POR QUE O SERVIDOR DEVOLVE HTML, E NÃO JSON PARA O NAVEGADOR MONTAR.</b> A API
 * JSON continua existindo e continua sendo a mesma. Estas rotas são outra coisa: elas
 * devolvem a tela pronta. A consequência prática é que a página funciona com o JavaScript
 * desligado nos caminhos de leitura, e que a regra de negócio nunca é reescrita em
 * JavaScript para "validar antes de enviar" — validação duplicada é validação que
 * diverge.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nenhuma regra vive aqui.</b> Este resource traduz formulário em chamada de
 *       caso de uso e resultado em template. Data inválida, documento repetido e cliente
 *       inexistente são decididos onde já eram.</li>
 *   <li><b>Erro vira tela, não página branca.</b> Toda falha esperada volta como o mesmo
 *       formulário, com a mensagem e o campo apontado — o que a pessoa digitou continua
 *       lá. Formulário que se apaga ao errar faz a pessoa desistir na segunda tentativa.</li>
 *   <li><b>A exclusão exige confirmação em tela própria</b>, com os dados do cliente à
 *       vista. É irreversível, e o botão de excluir fica ao lado do de alterar numa lista
 *       de nomes parecidos.</li>
 *   <li><b>Toda página passa pela {@link MolduraDaPagina}</b>. Não é proteção contra
 *       esquecimento — o Qute já é estrito e derruba a renderização com 500 se faltar
 *       qualquer chave. É contra a DIVERGÊNCIA: quatro linhas copiadas em treze telas
 *       acabam com uma delas formatando o relógio diferente das outras doze.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link ErroDePipeline} conhecido vira a mesma
 * tela com aviso e status 200 — porque, para o HTMX, um 4xx não troca o conteúdo por
 * padrão, e a pessoa ficaria olhando o formulário sem saber que algo falhou. Falha
 * inesperada sobe para o mapeador de borda.</p>
 */
@Path("/clientes")
@Produces(MediaType.TEXT_HTML)
@Transactional
public class ClientePaginasResource {

    /** Teto de itens por página. O mesmo do caso de uso, declarado aqui para a tela. */
    private static final int TAMANHO_PAGINA = 20;

    @Inject
    MolduraDaPagina moldura;

    @Inject
    ConsultarClientesUseCase consultar;

    @Inject
    CadastrarClienteUseCase cadastrar;

    @Inject
    AlterarClienteUseCase alterar;

    @Inject
    ExcluirClienteUseCase excluir;

    @Inject
    @Location("paginas/clientes/listar/pagina.html")
    Template telaListar;

    @Inject
    @Location("paginas/clientes/listar/fragmento-lista.html")
    Template fragmentoLista;

    @Inject
    @Location("paginas/clientes/cadastrar/pagina.html")
    Template telaCadastrar;

    @Inject
    @Location("paginas/clientes/buscar/pagina.html")
    Template telaBuscar;

    @Inject
    @Location("paginas/clientes/buscar/fragmento-resultado.html")
    Template fragmentoResultado;

    @Inject
    @Location("paginas/clientes/detalhe/pagina.html")
    Template telaDetalhe;

    @Inject
    @Location("paginas/clientes/alterar/pagina.html")
    Template telaAlterar;

    @Inject
    @Location("paginas/clientes/excluir/pagina.html")
    Template telaExcluir;

    // ------------------------------------------------------------------ listar

    @GET
    @Path("/listar")
    public TemplateInstance listar(@QueryParam("termo") String termo,
                                   @QueryParam("pagina") @DefaultValue("0") int pagina) {
        return moldura.vestir(telaListar
                .data("termo", termo == null ? "" : termo)
                .data("total", consultar.contar()), "clientes");
    }

    /**
     * Só a lista — é o que o HTMX troca ao digitar na busca ou ao paginar.
     *
     * <p>Devolver a página inteira aqui faria o campo de busca ser recriado a cada tecla,
     * e o cursor saltaria para fora dele. Trocar só a lista é o que mantém o foco.</p>
     */
    @GET
    @Path("/fragmento/lista")
    public TemplateInstance fragmentoDaLista(@QueryParam("termo") String termo,
                                             @QueryParam("pagina") @DefaultValue("0") int pagina) {
        int p = Math.max(0, pagina);
        List<Cliente> clientes = (termo == null || termo.isBlank())
                ? consultar.listar(p, TAMANHO_PAGINA)
                : consultar.pesquisar(termo, p, TAMANHO_PAGINA);

        return fragmentoLista
                .data("clientes", clientes)
                .data("termo", termo == null ? "" : termo)
                .data("pagina", p)
                // "Tem próxima?" sem contar a base inteira: se veio página cheia, PODE
                // haver mais. Contar a cada tecla digitada custaria uma varredura extra
                // por letra, e o ganho seria saber o número exato de páginas que ninguém
                // usa numa busca incremental.
                .data("temProxima", clientes.size() == TAMANHO_PAGINA)
                .data("vazio", clientes.isEmpty());
    }

    // --------------------------------------------------------------- cadastrar

    @GET
    @Path("/cadastrar")
    public TemplateInstance formularioDeCadastro() {
        return moldura.vestir(telaCadastrar
                .data("criado", null).data("erro", null).data("campo", null)
                .data("nome", "").data("sobrenome", "")
                .data("dataNascimento", "").data("documento", ""), "clientes");
    }

    @POST
    @Path("/cadastrar")
    public TemplateInstance cadastrar(@RestForm String nome, @RestForm String sobrenome,
                                      @RestForm("dataNascimento") String dataNascimento,
                                      @RestForm String documento) {
        try {
            Cliente novo = cadastrar.executar(nome, sobrenome,
                    lerData(dataNascimento), documento);
            return moldura.vestir(telaCadastrar
                    .data("criado", novo).data("erro", null).data("campo", null)
                    .data("nome", "").data("sobrenome", "")
                    .data("dataNascimento", "").data("documento", ""), "clientes");
        } catch (ErroDePipeline falha) {
            // O que a pessoa digitou VOLTA preenchido. Formulario que se apaga ao errar
            // faz desistir na segunda tentativa — e o erro mais comum aqui e um digito
            // de CPF, que nao justifica redigitar tudo.
            return moldura.vestir(telaCadastrar
                    .data("criado", null)
                    .data("erro", falha.getMessage()).data("campo", falha.alvo())
                    .data("nome", nome).data("sobrenome", sobrenome)
                    .data("dataNascimento", dataNascimento).data("documento", documento),
                    "clientes");
        }
    }

    // ------------------------------------------------------------------ buscar

    @GET
    @Path("/buscar")
    public TemplateInstance formularioDeBusca() {
        return moldura.vestir(telaBuscar.data("documento", ""), "clientes");
    }

    @GET
    @Path("/fragmento/por-documento")
    public TemplateInstance buscarPorDocumento(@QueryParam("documento") String documento) {
        if (documento == null || documento.isBlank()) {
            return fragmentoResultado.data("cliente", null).data("erro", null)
                    .data("procurou", false);
        }
        try {
            return fragmentoResultado
                    .data("cliente", consultar.porDocumento(documento).orElse(null))
                    .data("erro", null).data("procurou", true);
        } catch (ErroDePipeline falha) {
            // Documento MALFORMADO e diferente de documento que nao existe: o primeiro
            // pede corrigir o que se digitou, o segundo pede cadastrar. Dizer "nao
            // encontrado" para um CPF de 10 digitos manda procurar a pessoa errada.
            return fragmentoResultado.data("cliente", null)
                    .data("erro", falha.getMessage()).data("procurou", true);
        }
    }

    // ----------------------------------------------------------------- detalhe

    @GET
    @Path("/{id}")
    public TemplateInstance detalhe(@PathParam("id") long id) {
        return moldura.vestir(telaDetalhe.data("cliente", consultar.exigirPorId(id)), "clientes");
    }

    // ----------------------------------------------------------------- alterar

    @GET
    @Path("/{id}/alterar")
    public TemplateInstance formularioDeAlteracao(@PathParam("id") long id) {
        Cliente c = consultar.exigirPorId(id);
        return moldura.vestir(telaAlterar
                .data("cliente", c).data("salvo", false)
                .data("erro", null).data("campo", null)
                .data("nome", c.nome()).data("sobrenome", c.sobrenome())
                .data("dataNascimento", c.dataNascimento().toString())
                .data("documento", c.documento().digitos()), "clientes");
    }

    @POST
    @Path("/{id}/alterar")
    public TemplateInstance alterar(@PathParam("id") long id,
                                    @RestForm String nome, @RestForm String sobrenome,
                                    @RestForm("dataNascimento") String dataNascimento,
                                    @RestForm String documento) {
        Cliente antes = consultar.exigirPorId(id);
        try {
            Cliente novo = alterar.executar(id, nome, sobrenome,
                    lerData(dataNascimento), documento);
            return moldura.vestir(telaAlterar
                    .data("cliente", novo).data("salvo", true)
                    .data("erro", null).data("campo", null)
                    .data("nome", novo.nome()).data("sobrenome", novo.sobrenome())
                    .data("dataNascimento", novo.dataNascimento().toString())
                    .data("documento", novo.documento().digitos()), "clientes");
        } catch (ErroDePipeline falha) {
            return moldura.vestir(telaAlterar
                    .data("cliente", antes).data("salvo", false)
                    .data("erro", falha.getMessage()).data("campo", falha.alvo())
                    .data("nome", nome).data("sobrenome", sobrenome)
                    .data("dataNascimento", dataNascimento).data("documento", documento),
                    "clientes");
        }
    }

    // ----------------------------------------------------------------- excluir

    /**
     * A tela de confirmação, com os dados do cliente à vista.
     *
     * <p>Existe separada de propósito. Excluir é irreversível, e o botão fica ao lado do
     * de alterar numa lista de nomes parecidos: quem clica errado precisa ver <b>quem</b>
     * vai sumir antes de confirmar, não depois.</p>
     */
    @GET
    @Path("/{id}/excluir")
    public TemplateInstance confirmacaoDeExclusao(@PathParam("id") long id) {
        return moldura.vestir(telaExcluir
                .data("cliente", consultar.exigirPorId(id))
                .data("excluido", false), "clientes");
    }

    @POST
    @Path("/{id}/excluir")
    public TemplateInstance excluir(@PathParam("id") long id) {
        Cliente cliente = consultar.exigirPorId(id);
        excluir.executar(id);
        return moldura.vestir(telaExcluir
                .data("cliente", cliente)
                .data("excluido", true), "clientes");
    }

    // ------------------------------------------------------------------- apoio

    /**
     * Lê a data do formulário.
     *
     * <p>O {@code <input type="date">} entrega sempre {@code AAAA-MM-DD}, mas o
     * formulário pode chegar de outro lugar — e um {@code DateTimeParseException} cru
     * viraria erro 500 numa tela em que a pessoa só digitou a data errada.</p>
     *
     * @throws DataDoFormularioInvalidaException quando o texto não é uma data ISO
     */
    private static LocalDate lerData(String texto) {
        try {
            return LocalDate.parse(texto == null ? "" : texto.strip());
        } catch (DateTimeParseException e) {
            throw new DataDoFormularioInvalidaException(texto, e);
        }
    }
}
