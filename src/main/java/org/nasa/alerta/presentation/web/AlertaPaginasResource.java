package org.nasa.alerta.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.nasa.alerta.application.MontarAlertaUseCase;
import org.nasa.alerta.domain.exceptions.CepSemPosicaoException;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;

/**
 * A tela de alerta — o e-mail que você receberia, mostrado na hora.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o produto do sistema. A pessoa informa o e-mail e o CEP,
 * e vê imediatamente a mensagem que seria enviada, com os desastres que a NASA publicou
 * perto dali.</p>
 *
 * <p><b>NADA É GRAVADO — nem o e-mail, nem a consulta.</b> Sem tabela de inscritos não há
 * lista para vazar, não há dado pessoal para proteger, e não há formulário público
 * escrevendo no banco. Também é por isso que <b>não há limite por origem aqui</b>: a
 * proteção existia para impedir o banco de encher, e não há mais o que encher. O que sobra
 * de custo por requisição — a consulta de CEP — já tem limite próprio no adaptador do
 * Nominatim, exigido pela política de uso dele.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Consultar é POST</b>, e não GET, mesmo sem escrever no banco: o e-mail vai no
 *       corpo, e um GET o poria na URL — onde ele fica no histórico do navegador, no log do
 *       servidor e no cabeçalho {@code Referer} enviado a terceiros.</li>
 *   <li><b>O e-mail some no fim da requisição.</b> Ele aparece na saudação da tela e em
 *       lugar nenhum mais: nem em log, nem no corpo da mensagem, nem em disco.</li>
 *   <li><b>CEP inexistente e CEP sem posição são mensagens DIFERENTES.</b> Uma pede
 *       corrigir os dígitos; a outra não pede nada de quem consultou — mandá-la "conferir o
 *       CEP" seria pedir que corrija o que está certo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Tudo vira aviso na própria tela, com o
 * formulário preservado. Nenhum caminho leva a página de erro: quem chegou aqui quer saber
 * se há desastre perto, e uma tela de erro não responde isso nem depois de recarregar.</p>
 */
@Path("/alertas")
@Produces(MediaType.TEXT_HTML)
public class AlertaPaginasResource {

    @Inject
    MolduraDaPagina moldura;

    @Inject
    MontarAlertaUseCase montar;

    @Inject
    @Location("paginas/alertas/painel/pagina.html")
    Template tela;

    @GET
    public TemplateInstance pagina() {
        return moldura.vestir(vazia(), "alertas");
    }

    /**
     * Monta e mostra.
     *
     * <p><b>POST mesmo sem gravar nada.</b> O e-mail vai no corpo da requisição; num GET ele
     * iria na URL, e URL fica no histórico do navegador, no log de acesso do servidor e no
     * cabeçalho {@code Referer} que o navegador manda para qualquer link que a pessoa clicar
     * depois. Três lugares onde um endereço de e-mail não deveria estar.</p>
     */
    @POST
    public TemplateInstance consultar(@FormParam("email") String email,
                                      @FormParam("cep") String cep,
                                      @FormParam("raioKm") Double raioKm,
                                      @FormParam("dias") @DefaultValue("30") Integer dias) {
        try {
            var mensagem = montar.executar(email, cep, raioKm, dias);
            return moldura.vestir(comum()
                    .data("mensagem", mensagem)
                    .data("email", email)
                    .data("cepDigitado", cep)
                    .data("erro", null)
                    .data("corrigirCep", false), "alertas");

        } catch (CepSemPosicaoException semPosicao) {
            // AS DUAS COISAS SAO DIFERENTES, e a tela precisa saber qual foi: "CEP nao
            // existe" pede corrigir os digitos; "CEP existe sem posicao" nao pede nada de
            // quem consultou, e mandar conferir seria pedir que corrija o que esta certo.
            return moldura.vestir(comum()
                    .data("mensagem", null)
                    .data("email", email)
                    .data("cepDigitado", cep)
                    .data("erro", semPosicao.getMessage())
                    .data("corrigirCep", semPosicao.cepInexistente()), "alertas");

        } catch (ErroDePipeline falha) {
            // Dado invalido, provedor fora. O formulario CONTINUA preenchido: obrigar a
            // redigitar tudo por causa de um digito e o que faz desistir.
            return moldura.vestir(comum()
                    .data("mensagem", null)
                    .data("email", email)
                    .data("cepDigitado", cep)
                    .data("erro", falha.getMessage())
                    .data("corrigirCep", false), "alertas");
        }
    }

    /**
     * O estado inicial da tela.
     *
     * <p>Fornece TODAS as chaves que o template lê, mesmo as nulas. O Qute é estrito: chave
     * ausente é 500, não campo vazio — e este projeto já pagou isso quatro vezes.</p>
     */
    private TemplateInstance vazia() {
        return comum()
                .data("mensagem", null)
                .data("email", null)
                .data("cepDigitado", null)
                .data("erro", null)
                .data("corrigirCep", false);
    }

    private TemplateInstance comum() {
        return tela
                .data("raioPadrao", MontarAlertaUseCase.RAIO_PADRAO_KM)
                .data("diasPadrao", MontarAlertaUseCase.DIAS_PADRAO);
    }
}
