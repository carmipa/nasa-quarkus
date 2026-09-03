package org.nasa.core.presentation.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Instant;
import java.util.Date;

/**
 * As duas portas da sessão: entrar pelo GitHub e sair.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A área de operação já é fechada pela política de rotas
 * — quem tenta {@code /desastres} sem sessão é mandado ao GitHub automaticamente. Falta
 * o caminho de quem está numa página <i>pública</i> e quer entrar antes de precisar:
 * sem um endereço para o botão apontar, entrar exigiria bater numa porta fechada de
 * propósito, o que é uma forma esquisita de convidar alguém.</p>
 *
 * <p><b>POR QUE ESTA CLASSE NÃO TEM ANOTAÇÃO DE SEGURANÇA.</b> {@code /login/github} não
 * está na lista de rotas públicas, então cai na regra {@code /*} e já exige sessão. Uma
 * {@code @Authenticated} aqui repetiria a mesma decisão num segundo lugar — e o segundo
 * lugar é o que fica para trás quando a política muda. Ela também quebraria dev, onde
 * não existe autenticador nenhum.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Sair é sempre possível, inclusive sem
 * sessão: apagar um cookie que não existe é um não-evento, e "sair" que devolve erro
 * deixa a pessoa presa numa sessão que ela pediu para encerrar.</p>
 */
@Path("/login")
public class AutenticacaoResource {

    /**
     * O cookie de sessão do Quarkus OIDC no locatário padrão.
     *
     * <p>É este nome que a extensão grava e lê; apagar outro nome devolveria "saiu" com
     * a sessão intacta — a pior das duas mentiras possíveis nesta tela.</p>
     */
    private static final String COOKIE_DA_SESSAO = "q_session";

    /**
     * Entrar. Chegar aqui sem sessão dispara a ida ao GitHub; com sessão, cai direto no
     * painel, que é o que a pessoa queria ao clicar em "Entrar".
     */
    @GET
    @Path("/github")
    public Response entrar() {
        return Response.seeOther(URI.create("/desastres")).build();
    }

    /**
     * Sair — <b>localmente</b>, e isso precisa ser dito.
     *
     * <p>O GitHub é OAuth2, não OIDC: não existe endpoint de fim de sessão para onde
     * mandar o navegador. O que se pode encerrar é a sessão <i>desta</i> aplicação, e é
     * o que acontece. A sessão do GitHub no navegador continua — quem clicar em entrar
     * de novo volta sem digitar senha, e isso não é defeito: é o que "sair daqui"
     * significa quando o login é de terceiro.</p>
     */
    @GET
    @Path("/sair")
    public Response sair() {
        NewCookie apagado = new NewCookie.Builder(COOKIE_DA_SESSAO)
                .value("")
                .path("/")
                // `maxAge(0)` e o que apaga o cookie em qualquer navegador atual.
                .maxAge(0)
                // `Expires` e o mecanismo LEGADO, para navegador que ignora `Max-Age`.
                // Os dois juntos porque um cookie de sessao que nao morre e pior que
                // um cabecalho a mais.
                //
                // `Date.from(Instant.EPOCH)` E NAO `new Date(0)`: a catraca de fuso
                // proibe o CONSTRUTOR de `java.util.Date`, e esta certa — `new Date()`
                // carrega o fuso da JVM implicitamente. A fabrica estatica recebe um
                // `Instant`, que e UTC por construcao, e o instante fica explicito no
                // codigo em vez de escondido num zero.
                //
                // A catraca pegou isto na primeira execucao depois de o arquivo entrar
                // no projeto. Era exatamente para isso que ela existia.
                .expiry(Date.from(Instant.EPOCH))
                .httpOnly(true)
                .build();
        return Response.seeOther(URI.create("/")).cookie(apagado).build();
    }
}
