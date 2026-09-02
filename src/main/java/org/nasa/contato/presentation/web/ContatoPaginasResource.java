package org.nasa.contato.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.nasa.contato.application.AlterarContatoUseCase;
import org.nasa.contato.application.CadastrarContatoUseCase;
import org.nasa.contato.application.ConsultarContatosUseCase;
import org.nasa.contato.application.ExcluirContatoUseCase;
import org.nasa.contato.application.VincularContatoAoClienteUseCase;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.TipoContato;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;

import java.util.List;

/**
 * As telas de contato — listar, cadastrar, alterar e excluir.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É onde se define <b>por onde</b> uma pessoa é avisada. A
 * decisão que essa tela precisa deixar clara é uma só: <b>este contato recebe alerta de
 * desastre?</b> Só o tipo {@code EMERGÊNCIA} recebe, e no legado nada dizia isso — dava
 * para cadastrar um contato achando que a cobertura existia.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A consequência do tipo é dita ANTES de salvar</b>, e não descoberta depois. O
 *       formulário explica o que cada tipo faz, e a tela de confirmação repete.</li>
 *   <li><b>Excluir tem tela própria</b>, com os dados à vista — e avisa quando o contato
 *       excluído era de emergência, porque ali alguém deixa de ser avisado.</li>
 *   <li><b>Erro devolve o formulário PREENCHIDO.</b> O engano mais comum é um dígito de
 *       telefone, e não justifica redigitar tudo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha esperada volta como a mesma tela, com
 * aviso e campo apontado. Falha inesperada sobe para o mapeador de borda.</p>
 */
@Path("/contatos")
@Produces(MediaType.TEXT_HTML)
@Transactional
public class ContatoPaginasResource {

    private static final int TAMANHO_PAGINA = 20;

    @Inject
    MolduraDaPagina moldura;

    @Inject
    CadastrarContatoUseCase cadastrar;

    @Inject
    ConsultarContatosUseCase consultar;

    @Inject
    AlterarContatoUseCase alterar;

    @Inject
    ExcluirContatoUseCase excluir;

    @Inject
    VincularContatoAoClienteUseCase vincular;

    @Inject
    @Location("paginas/contatos/listar/pagina.html")
    Template telaListar;

    @Inject
    @Location("paginas/contatos/listar/fragmento-lista.html")
    Template fragmentoLista;

    @Inject
    @Location("paginas/contatos/formulario/pagina.html")
    Template telaFormulario;

    @Inject
    @Location("paginas/contatos/excluir/pagina.html")
    Template telaExcluir;

    // ------------------------------------------------------------------ listar

    @GET
    @Path("/listar")
    public TemplateInstance listar() {
        return moldura.vestir(telaListar
                .data("total", consultar.contar()), "contatos");
    }

    @GET
    @Path("/fragmento/lista")
    public TemplateInstance fragmentoDaLista(@QueryParam("termo") String termo,
                                             @QueryParam("tipo") String tipo,
                                             @QueryParam("pagina") @DefaultValue("0") int pagina) {
        int p = Math.max(0, pagina);
        List<Contato> contatos;
        if (tipo != null && !tipo.isBlank()) {
            contatos = consultar.porTipo(tipo, p, TAMANHO_PAGINA);
        } else if (termo != null && !termo.isBlank()) {
            contatos = consultar.pesquisar(termo, p, TAMANHO_PAGINA);
        } else {
            contatos = consultar.listar(p, TAMANHO_PAGINA);
        }
        return fragmentoLista
                .data("contatos", contatos)
                .data("termo", termo == null ? "" : termo)
                .data("tipo", tipo == null ? "" : tipo)
                .data("pagina", p)
                .data("temProxima", contatos.size() == TAMANHO_PAGINA)
                .data("vazio", contatos.isEmpty());
    }

    // --------------------------------------------------------------- formulário

    @GET
    @Path("/cadastrar")
    public TemplateInstance formularioDeCadastro() {
        return moldura.vestir(vazio(telaFormulario)
                .data("tipos", TipoContato.values())
                .data("edicao", false).data("contato", null), "contatos");
    }

    @POST
    @Path("/cadastrar")
    public TemplateInstance cadastrar(@RestForm String ddd, @RestForm String telefone,
                                      @RestForm String celular, @RestForm String whatsapp,
                                      @RestForm String email,
                                      @RestForm("tipoContato") String tipoContato,
                                      @RestForm("clienteId") String clienteId) {
        try {
            Contato criado = cadastrar.executar(ddd, telefone, celular, whatsapp,
                    email, tipoContato);
            // Vincular ao cliente na MESMA operacao: um contato de emergencia solto nunca
            // recebe aviso, porque a varredura parte dos enderecos do cliente.
            if (clienteId != null && !clienteId.isBlank()) {
                vincular.executar(criado.id(), Long.parseLong(clienteId.strip()));
            }
            return moldura.vestir(vazio(telaFormulario)
                    .data("tipos", TipoContato.values())
                    .data("edicao", false).data("contato", null)
                    .data("criado", criado).data("salvo", false), "contatos");
        } catch (ErroDePipeline | NumberFormatException falha) {
            return moldura.vestir(telaFormulario
                    .data("tipos", TipoContato.values())
                    .data("edicao", false).data("contato", null).data("criado", null)
                    .data("salvo", false)
                    .data("erro", mensagem(falha)).data("campo", alvo(falha))
                    .data("ddd", ddd).data("telefone", telefone).data("celular", celular)
                    .data("whatsapp", whatsapp).data("email", email)
                    .data("tipoContato", tipoContato).data("clienteId", clienteId), "contatos");
        }
    }

    @GET
    @Path("/{id}/alterar")
    public TemplateInstance formularioDeAlteracao(@PathParam("id") long id) {
        Contato c = consultar.exigirPorId(id);
        return moldura.vestir(telaFormulario
                .data("tipos", TipoContato.values())
                .data("edicao", true).data("contato", c).data("criado", null)
                .data("salvo", false).data("erro", null).data("campo", null)
                .data("ddd", c.ddd()).data("telefone", c.telefone())
                .data("celular", c.celular()).data("whatsapp", c.whatsapp())
                .data("email", c.email().valor()).data("tipoContato", c.tipo().name())
                .data("clienteId", ""), "contatos");
    }

    @POST
    @Path("/{id}/alterar")
    public TemplateInstance alterar(@PathParam("id") long id,
                                    @RestForm String ddd, @RestForm String telefone,
                                    @RestForm String celular, @RestForm String whatsapp,
                                    @RestForm String email,
                                    @RestForm("tipoContato") String tipoContato) {
        Contato antes = consultar.exigirPorId(id);
        try {
            Contato salvo = alterar.executar(id, ddd, telefone, celular, whatsapp,
                    email, tipoContato);
            return moldura.vestir(telaFormulario
                    .data("tipos", TipoContato.values())
                    .data("edicao", true).data("contato", salvo).data("criado", null)
                    .data("salvo", true).data("erro", null).data("campo", null)
                    .data("ddd", salvo.ddd()).data("telefone", salvo.telefone())
                    .data("celular", salvo.celular()).data("whatsapp", salvo.whatsapp())
                    .data("email", salvo.email().valor())
                    .data("tipoContato", salvo.tipo().name())
                    .data("clienteId", ""), "contatos");
        } catch (ErroDePipeline falha) {
            return moldura.vestir(telaFormulario
                    .data("tipos", TipoContato.values())
                    .data("edicao", true).data("contato", antes).data("criado", null)
                    .data("salvo", false)
                    .data("erro", falha.getMessage()).data("campo", falha.alvo())
                    .data("ddd", ddd).data("telefone", telefone).data("celular", celular)
                    .data("whatsapp", whatsapp).data("email", email)
                    .data("tipoContato", tipoContato).data("clienteId", ""), "contatos");
        }
    }

    // ----------------------------------------------------------------- excluir

    @GET
    @Path("/{id}/excluir")
    public TemplateInstance confirmacaoDeExclusao(@PathParam("id") long id) {
        return moldura.vestir(telaExcluir
                .data("contato", consultar.exigirPorId(id))
                .data("excluido", false), "contatos");
    }

    @POST
    @Path("/{id}/excluir")
    public TemplateInstance excluir(@PathParam("id") long id) {
        Contato contato = consultar.exigirPorId(id);
        excluir.executar(id);
        return moldura.vestir(telaExcluir
                .data("contato", contato)
                .data("excluido", true), "contatos");
    }

    // ------------------------------------------------------------------- apoio

    /** O formulário em branco — os mesmos campos, todos vazios. */
    private static TemplateInstance vazio(Template t) {
        return t.data("erro", null).data("campo", null).data("salvo", false)
                .data("criado", null)
                .data("ddd", "").data("telefone", "").data("celular", "")
                .data("whatsapp", "").data("email", "")
                .data("tipoContato", "PRINCIPAL").data("clienteId", "");
    }

    private static String mensagem(Exception falha) {
        return falha instanceof NumberFormatException
                // Um identificador de cliente com letra e engano de digitacao, nao falha
                // do sistema — e a mensagem precisa dizer isso.
                ? "o identificador do cliente precisa ser um numero"
                : falha.getMessage();
    }

    private static String alvo(Exception falha) {
        return falha instanceof ErroDePipeline e ? e.alvo() : "clienteId";
    }
}
