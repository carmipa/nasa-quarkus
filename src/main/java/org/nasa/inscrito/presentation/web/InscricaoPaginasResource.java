package org.nasa.inscrito.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;
import org.nasa.inscrito.application.ConsultarInscritosUseCase;
import org.nasa.inscrito.application.InscreverUseCase;
import org.nasa.inscrito.domain.Inscrito;

/**
 * A tela de inscrição — a porta de entrada do sistema.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Alguém informa nome, e-mail e o CEP de onde está, e passa
 * a ser avisado quando um desastre entra no raio dela. É o que substituiu três telas de
 * CRUD — cliente, contato e endereço — que modelavam gestão de clientes num sistema que
 * não gerencia clientes: ele avisa gente sobre desastre.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Inscrever é POST</b>, nunca GET: escreve no banco, e um GET que escreve é
 *       executado por rastreador e por pré-carregamento de navegador.</li>
 *   <li><b>E-mail repetido produz uma mensagem que ACALMA, não um erro.</b> "Você já está
 *       inscrito — o aviso já vai chegar" é a verdade, e é o que a pessoa precisa ler
 *       depois de clicar duas vezes porque a página demorou.</li>
 *   <li><b>Inscrição sem coordenada é DECLARADA na tela.</b> Ela existe e não recebe alerta
 *       de proximidade; deixá-la parecer normal faria alguém esperar um aviso que nunca
 *       vem — e essa espera é o pior defeito possível num sistema de alerta.</li>
 *   <li><b>Nenhuma regra vive aqui.</b> Este resource traduz formulário em caso de uso.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Dado inválido vira aviso na própria tela,
 * nomeando o campo, com o que foi digitado preservado — obrigar a redigitar tudo por causa
 * de um dígito é o que faz desistir. Falha inesperada sobe para o mapeador de borda.</p>
 */
@Path("/inscricao")
@Produces(MediaType.TEXT_HTML)
@Transactional
public class InscricaoPaginasResource {

    private static final int TAMANHO_PAGINA = 20;

    @Inject
    MolduraDaPagina moldura;

    @Inject
    InscreverUseCase inscrever;

    @Inject
    ConsultarInscritosUseCase consultar;

    @Inject
    @Location("paginas/inscricao/pagina.html")
    Template tela;

    @GET
    public TemplateInstance pagina(@QueryParam("pagina") @DefaultValue("0") int pagina) {
        return moldura.vestir(comum(pagina).data("resultado", null).data("erro", null)
                .data("jaInscrito", false), "inscricao");
    }

    /**
     * Grava a inscrição.
     *
     * <p><b>Devolve a página inteira</b>, e não um fragmento: é o caminho que precisa
     * funcionar sem JavaScript, e a inscrição é a única coisa que o sistema pede a quem
     * chega. Quebrar isso por elegância seria trocar a função pela forma.</p>
     */
    @POST
    public TemplateInstance inscrever(@FormParam("nome") String nome,
                                      @FormParam("email") String email,
                                      @FormParam("telefone") String telefone,
                                      @FormParam("cep") String cep,
                                      @FormParam("raioKm") Double raioKm) {
        try {
            var r = inscrever.executar(nome, email, telefone, cep, raioKm);
            return moldura.vestir(comum(0).data("resultado", r).data("erro", null)
                    .data("jaInscrito", false), "inscricao");

        } catch (ErroDePipeline recusa) {
            // E-mail ja inscrito e RECUSA, nao falha: a pessoa ja esta na lista. A tela
            // separa os dois porque a mensagem certa e oposta — uma acalma, a outra pede
            // correcao.
            boolean jaInscrito = recusa.causaRaiz() == org.nasa.core.erro.CausaRaiz.CONFLITO_DE_ESTADO;
            return moldura.vestir(comum(0).data("resultado", null)
                    .data("erro", recusa.getMessage())
                    .data("jaInscrito", jaInscrito), "inscricao");
        }
    }

    /** Cancela. POST porque ESCREVE, e o cancelamento não apaga — só desativa. */
    @POST
    @Path("/{id}/cancelar")
    public TemplateInstance cancelar(@PathParam("id") long id) {
        boolean mudou = consultar.cancelar(id);
        return moldura.vestir(comum(0).data("resultado", null)
                // Cancelar duas vezes NAO e erro: e o clique repetido de sempre, e a
                // segunda vez simplesmente nao muda nada.
                .data("erro", mudou ? null : "esta inscricao ja estava cancelada")
                .data("jaInscrito", false), "inscricao");
    }

    /**
     * As chaves que TODO caminho fornece.
     *
     * <p>O Qute é estrito: chave ausente é 500, não campo vazio. Este projeto já pagou isso
     * três vezes — {@code criado}, {@code salvo} e {@code barras} —, e a correção é sempre a
     * mesma: um único lugar que fornece tudo o que o template lê.</p>
     */
    private TemplateInstance comum(int pagina) {
        int p = Math.max(0, pagina);
        var inscritos = consultar.listar(p, TAMANHO_PAGINA);
        return tela
                .data("inscritos", inscritos)
                .data("pagina", p)
                .data("temProxima", inscritos.size() == TAMANHO_PAGINA)
                .data("total", consultar.contar())
                .data("ativos", consultar.contarAtivos())
                // MOSTRADO na tela, nunca escondido: quem esta sem coordenada nao recebe
                // alerta de proximidade, e precisa saber disso.
                .data("semCoordenada", consultar.contarSemCoordenada())
                .data("raioPadrao", Inscrito.RAIO_PADRAO_KM);
    }
}
