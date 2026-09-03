package org.nasa.core.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * As páginas de erro do sistema — uma por classe de falha, em HTML ou JSON.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Erro é tela, e tela mal feita perde a pessoa. O que o
 * sistema mostrava era a página padrão do Quarkus: em desenvolvimento ela <b>lista todos os
 * endpoints da API</b> — a superfície inteira do sistema entregue a quem digitou um endereço
 * errado; em produção, uma folha branca com o número do status.</p>
 *
 * <p><b>A DECISÃO CENTRAL: quem pediu HTML recebe página, quem pediu JSON recebe JSON.</b> O
 * mesmo 404 serve um navegador e uma integração, e as duas precisam de coisas opostas. Mandar
 * HTML para um cliente de API entope o log dele com marcação; mandar JSON para um navegador
 * mostra chaves e colchetes na tela.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nenhum rastro de pilha, nome de classe, caminho de arquivo ou SQL chega ao
 *       visitante.</b> Isso é informação de infraestrutura para quem não deveria vê-la, e é
 *       o mapa que um atacante usa. O detalhe completo vai para o log, que tem dono.</li>
 *   <li><b>O status HTTP é o de verdade.</b> Uma página de erro que responde 200 é
 *       indistinguível de sucesso para rastreador, monitor e cliente de API — e o Google
 *       indexa a página de erro como se fosse conteúdo.</li>
 *   <li><b>Cada classe de erro diz O QUE FAZER</b>, e não só o que aconteceu. "Não
 *       encontrado" sem caminho de volta é um beco; a página oferece os endereços que
 *       existem.</li>
 *   <li><b>Falha ao renderizar a página de erro NÃO pode virar outro erro.</b> Se o template
 *       quebrar, a resposta cai para texto simples com o status correto. Erro dentro do
 *       tratador de erro é o laço que derruba o servidor.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> 5xx é registrado com a exceção inteira; 4xx é
 * registrado em DEBUG — um rastreador varrendo URLs inexistentes geraria milhares de linhas
 * de WARN e afogaria o erro de verdade.</p>
 */
@Provider
public class PaginasDeErro implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(PaginasDeErro.class);
    private static final String OPERACAO = "pagina-de-erro";

    @Inject
    MolduraDaPagina moldura;

    @Inject
    HttpHeaders cabecalhos;

    @Inject
    @Location("paginas/erro/pagina.html")
    Template tela;

    @Override
    public Response toResponse(Throwable falha) {
        int status = statusDe(falha);
        var classe = ClasseDeErro.de(status);

        registrar(status, classe, falha);

        if (querHtml()) {
            return emHtml(status, classe);
        }
        return emJson(status, classe);
    }

    /**
     * O status HTTP da falha.
     *
     * <p>Uma {@link jakarta.ws.rs.WebApplicationException} já traz o seu — é o caso do 404
     * de rota inexistente e do 405 de método errado. Qualquer outra coisa é 500: um erro que
     * não sabemos classificar <b>não</b> pode virar 200 nem 400, porque os dois afirmariam
     * algo sobre o pedido que não se sabe.</p>
     */
    private static int statusDe(Throwable falha) {
        if (falha instanceof jakarta.ws.rs.WebApplicationException web) {
            return web.getResponse().getStatus();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    /**
     * Se o cliente quer HTML.
     *
     * <p><b>Navegador pede {@code text/html} explicitamente</b> no {@code Accept}; cliente
     * de API pede {@code application/json} ou nada. O caso sem {@code Accept} cai em JSON de
     * propósito: quem não declara o que aceita é, quase sempre, {@code curl} ou uma
     * integração — e para eles JSON é útil e HTML é ruído.</p>
     */
    private boolean querHtml() {
        try {
            var aceitos = cabecalhos.getAcceptableMediaTypes();
            for (var tipo : aceitos) {
                if (MediaType.TEXT_HTML_TYPE.isCompatible(tipo)
                        && !tipo.isWildcardType()) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException semCabecalho) {
            // Sem cabecalho legivel, JSON: e o formato que nao depende de moldura, de
            // template e de CSS — tres coisas que podem estar quebradas justamente agora.
            return false;
        }
    }

    private Response emHtml(int status, ClasseDeErro classe) {
        try {
            var instancia = moldura.vestir(tela
                    .data("status", status)
                    .data("titulo", classe.titulo())
                    .data("explicacao", classe.explicacao())
                    .data("oQueFazer", classe.oQueFazer())
                    .data("mostrarAtalhos", classe.mostrarAtalhos()), "erro");

            return Response.status(status)
                    .type(MediaType.TEXT_HTML)
                    .entity(instancia)
                    .build();

        } catch (RuntimeException naoRenderizou) {
            // ERRO DENTRO DO TRATADOR DE ERRO é o laço que derruba o servidor. Aqui ele
            // termina em texto simples, com o status certo — feio, e correto.
            LOG.error(Registro.recusa(OPERACAO, String.valueOf(status),
                    "TEMPLATE_DE_ERRO_FALHOU"), naoRenderizou);
            return Response.status(status)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(status + " — " + classe.titulo())
                    .build();
        }
    }

    private Response emJson(int status, ClasseDeErro classe) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("status", status);
        corpo.put("erro", classe.titulo());
        corpo.put("explicacao", classe.explicacao());
        // NUNCA a exceção, o nome da classe ou o rastro: este corpo vai para fora.
        return Response.status(status).type(MediaType.APPLICATION_JSON).entity(corpo).build();
    }

    /**
     * O registro.
     *
     * <p><b>5xx com a exceção inteira; 4xx em DEBUG.</b> Um rastreador varrendo endereços
     * inexistentes gera milhares de 404 — em WARN, eles afogariam o erro de verdade, e é
     * assim que um 500 real passa despercebido por dias.</p>
     */
    private static void registrar(int status, ClasseDeErro classe, Throwable falha) {
        if (status >= 500) {
            LOG.error(Registro.recusa(OPERACAO, String.valueOf(status), classe.name()), falha);
        } else {
            LOG.debug(Registro.recusa(OPERACAO, String.valueOf(status), classe.name()));
        }
    }

    /**
     * As classes de erro que o sistema sabe explicar.
     *
     * <p>Cada uma diz <b>o que fazer</b>, e não só o que aconteceu. "Não encontrado" sem
     * caminho de volta é um beco — e a pessoa que chega num beco fecha a aba.</p>
     */
    enum ClasseDeErro {

        NAO_ENCONTRADO("Esta página não existe",
                "O endereço que você abriu não corresponde a nenhuma tela deste sistema.",
                "Pode ser um link antigo: o sistema mudou de forma algumas vezes, e telas de "
                        + "cadastro que existiam foram removidas de propósito.", true),

        METODO_ERRADO("Este endereço existe, mas não aceita esse tipo de pedido",
                "A tela está lá; o que não bate é a forma do pedido — por exemplo, abrir "
                        + "diretamente um endereço que só responde a envio de formulário.",
                "Volte pela navegação em vez de digitar o endereço.", true),

        PEDIDO_INVALIDO("Algum dado do pedido não serve",
                "O sistema recusou antes de tentar: um campo obrigatório em branco, um CEP "
                        + "com menos de oito dígitos, um raio fora da faixa aceita.",
                "Confira o que foi preenchido e tente de novo — a tela costuma dizer qual "
                        + "campo é.", false),

        SEM_PERMISSAO("Você não tem acesso a esta tela",
                "Algumas telas deste sistema são restritas — a telemetria, por exemplo, mede "
                        + "o funcionamento interno e não é pública.",
                "Se você deveria ter acesso, entre com sua conta.", true),

        FORA_DO_AR("Uma fonte externa não respondeu",
                "O sistema depende de serviços de terceiros — a NASA, os provedores de CEP, "
                        + "o GDACS. Quando um deles não responde, a tela que precisa dele "
                        + "para de funcionar; as outras continuam.",
                "Tente de novo em alguns minutos. Não é nada que você tenha feito.", true),

        FALHA_INTERNA("Algo quebrou aqui dentro",
                "O erro é nosso, não seu. Ele foi registrado com detalhe suficiente para ser "
                        + "encontrado e corrigido.",
                "Se acontecer de novo no mesmo lugar, vale relatar — o endereço da página "
                        + "ajuda a achar a causa.", true);

        private final String titulo;
        private final String explicacao;
        private final String oQueFazer;
        private final boolean mostrarAtalhos;

        ClasseDeErro(String titulo, String explicacao, String oQueFazer,
                     boolean mostrarAtalhos) {
            this.titulo = titulo;
            this.explicacao = explicacao;
            this.oQueFazer = oQueFazer;
            this.mostrarAtalhos = mostrarAtalhos;
        }

        String titulo() {
            return titulo;
        }

        String explicacao() {
            return explicacao;
        }

        String oQueFazer() {
            return oQueFazer;
        }

        /** Se vale oferecer os caminhos que existem. Num 400 não: a tela é a certa. */
        boolean mostrarAtalhos() {
            return mostrarAtalhos;
        }

        /**
         * A classe de um status.
         *
         * <p>Status desconhecido cai em {@link #FALHA_INTERNA} quando é 5xx e em
         * {@link #PEDIDO_INVALIDO} quando é 4xx — <b>nunca</b> numa página em branco. Uma
         * página de erro sem texto é indistinguível de uma página que não carregou.</p>
         */
        static ClasseDeErro de(int status) {
            return switch (status) {
                case 400, 422 -> PEDIDO_INVALIDO;
                case 401, 403 -> SEM_PERMISSAO;
                case 404, 410 -> NAO_ENCONTRADO;
                case 405 -> METODO_ERRADO;
                case 502, 503, 504 -> FORA_DO_AR;
                default -> status >= 500 ? FALHA_INTERNA : PEDIDO_INVALIDO;
            };
        }
    }
}
