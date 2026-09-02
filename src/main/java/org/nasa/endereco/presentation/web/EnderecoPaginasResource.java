package org.nasa.endereco.presentation.web;

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
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;
import org.nasa.endereco.application.CadastrarEnderecoUseCase;
import org.nasa.endereco.application.ConsultarCepUseCase;
import org.nasa.endereco.domain.Endereco;
import org.nasa.endereco.domain.ports.RepositorioDeEnderecosPort;

import java.util.List;

/**
 * As telas de endereço — com o CEP preenchendo o resto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É aqui que um cliente vira uma pessoa <b>localizável</b>.
 * Sem endereço com coordenada, o alerta de proximidade não tem o que comparar, e o cliente
 * existe no sistema sem nunca poder ser avisado.</p>
 *
 * <p><b>POR QUE ESTA TELA NÃO É UM PEDAÇO DO CADASTRO DE CLIENTE.</b> Seria mais cômodo
 * ter um formulário só. Mas a regra da arquitetura proíbe uma fatia conhecer outra, e um
 * resource que injetasse os casos de uso de {@code cliente} e de {@code endereco} faria a
 * guarda de fronteira reprovar o build. A solução não é afrouxar a regra: é notar que
 * <b>o HTMX cruza no nível HTTP</b>. A tela de cliente aponta para cá com um link, o
 * navegador faz a chamada, e em Java as duas fatias continuam sem se conhecer.</p>
 *
 * <p><b>O CEP PREENCHE, NUNCA SOBRESCREVE.</b> Quem corrigiu o nome da rua sabe algo que a
 * base do CEP ainda não sabe — normalmente porque a rua mudou de nome e a base não
 * atualizou. O preenchimento automático completa o que está vazio e para aí.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Provedor de CEP fora NÃO impede o cadastro.</b> O endereço entra com o que foi
 *       digitado, sem coordenada, e a tela <b>diz</b> que ele não entra no alerta.
 *       Transferir a indisponibilidade de um serviço de terceiro para o nosso cadastro
 *       seria o pior tipo de acoplamento.</li>
 *   <li><b>Coordenada ausente é declarada em voz alta.</b> 1 de cada 6 CEPs medidos volta
 *       sem ela; um endereço assim nunca gera aviso, e isso é invisível de outra forma.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> CEP inexistente vira aviso no próprio
 * formulário, com os campos preservados. Falha de banco sobe com causa-raiz.</p>
 */
@Path("/enderecos")
@Produces(MediaType.TEXT_HTML)
@Transactional
public class EnderecoPaginasResource {

    @Inject
    MolduraDaPagina moldura;

    @Inject
    ConsultarCepUseCase consultarCep;

    @Inject
    CadastrarEnderecoUseCase cadastrar;

    @Inject
    RepositorioDeEnderecosPort repositorio;

    @Inject
    @Location("paginas/enderecos/formulario/pagina.html")
    Template telaFormulario;

    @Inject
    @Location("paginas/enderecos/formulario/fragmento-cep.html")
    Template fragmentoCep;

    @Inject
    @Location("paginas/enderecos/listar/pagina.html")
    Template telaListar;

    @GET
    @Path("/listar")
    public TemplateInstance listar(@QueryParam("clienteId") Long clienteId) {
        List<Endereco> enderecos = clienteId == null
                ? repositorio.listar(0, 100)
                : repositorio.doCliente(clienteId);
        return moldura.vestir(telaListar
                .data("enderecos", enderecos)
                .data("clienteId", clienteId)
                .data("total", repositorio.contar())
                .data("vazio", enderecos.isEmpty()), "enderecos");
    }

    @GET
    @Path("/cadastrar")
    public TemplateInstance formulario(@QueryParam("clienteId") String clienteId) {
        return moldura.vestir(vazio(telaFormulario)
                .data("clienteId", clienteId == null ? "" : clienteId), "enderecos");
    }

    /**
     * Os campos que o CEP preenche — é o que o HTMX troca ao sair do campo de CEP.
     *
     * <p><b>Devolve os campos, não a página.</b> Trocar a página inteira apagaria o número
     * e o complemento que a pessoa já tivesse digitado — e são justamente os dois que o
     * CEP não sabe.</p>
     *
     * <p><b>Três estados, nunca dois:</b> "ainda não consultei", "o CEP não existe" e "o
     * provedor está fora". O terceiro é o mais importante: dizer "CEP não encontrado"
     * quando o serviço caiu faz a pessoa apagar um CEP que estava certo.</p>
     */
    @GET
    @Path("/fragmento/por-cep")
    public TemplateInstance porCep(@QueryParam("cep") String cep) {
        if (cep == null || cep.replaceAll("\\D", "").length() < 8) {
            // Menos de 8 digitos e CEP incompleto, nao CEP inexistente. Consultar aqui
            // devolveria "nao encontrado" para quem so nao terminou de digitar.
            return fragmentoCep.data("achado", null).data("erro", null)
                    .data("consultou", false).data("indisponivel", false);
        }
        try {
            var achado = consultarCep.executar(cep);
            return fragmentoCep
                    .data("achado", achado.orElse(null))
                    .data("erro", null)
                    .data("consultou", true)
                    .data("indisponivel", false);
        } catch (ErroDePipeline falha) {
            // Provedor fora e DIFERENTE de CEP inexistente, e a tela precisa dizer qual
            // dos dois — as reacoes sao opostas: tentar de novo, ou corrigir o que digitou.
            return fragmentoCep.data("achado", null)
                    .data("erro", falha.getMessage())
                    .data("consultou", true)
                    .data("indisponivel", true);
        }
    }

    @POST
    @Path("/cadastrar")
    public TemplateInstance cadastrar(@RestForm String cep, @RestForm String numero,
                                      @RestForm String logradouro, @RestForm String bairro,
                                      @RestForm String localidade, @RestForm String uf,
                                      @RestForm String complemento,
                                      @RestForm("clienteId") String clienteId) {
        try {
            Integer n = (numero == null || numero.isBlank())
                    ? null : Integer.valueOf(numero.replaceAll("\\D", ""));
            Long cliente = (clienteId == null || clienteId.isBlank())
                    ? null : Long.valueOf(clienteId.strip());

            Endereco criado = cadastrar.executar(cep, n, logradouro, bairro,
                    localidade, uf, complemento, cliente);

            return moldura.vestir(vazio(telaFormulario)
                    .data("clienteId", clienteId == null ? "" : clienteId)
                    .data("criado", criado), "enderecos");
        } catch (ErroDePipeline | NumberFormatException falha) {
            return moldura.vestir(telaFormulario
                    .data("criado", null)
                    .data("erro", falha instanceof NumberFormatException
                            ? "o numero e o identificador do cliente precisam ser numericos"
                            : falha.getMessage())
                    .data("campo", falha instanceof ErroDePipeline e ? e.alvo() : "numero")
                    .data("cep", cep).data("numero", numero).data("logradouro", logradouro)
                    .data("bairro", bairro).data("localidade", localidade).data("uf", uf)
                    .data("complemento", complemento)
                    .data("clienteId", clienteId == null ? "" : clienteId), "enderecos");
        }
    }

    @POST
    @Path("/{id}/excluir")
    public TemplateInstance excluir(@PathParam("id") long id,
                                    @QueryParam("clienteId") @DefaultValue("") String clienteId) {
        repositorio.remover(id);
        return listar(clienteId.isBlank() ? null : Long.valueOf(clienteId));
    }

    /** O formulário em branco — todas as chaves que o template lê, todas vazias. */
    private static TemplateInstance vazio(Template t) {
        return t.data("erro", null).data("campo", null).data("criado", null)
                .data("cep", "").data("numero", "").data("logradouro", "")
                .data("bairro", "").data("localidade", "").data("uf", "")
                .data("complemento", "");
    }
}
