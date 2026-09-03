package org.nasa.core.telemetria;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.nasa.core.tempo.Relogio;

import java.time.Duration;
import java.time.Instant;

/**
 * Mede toda requisição HTTP — sem que nenhum resource precise saber disso.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Responde "que telas as pessoas usam", "qual está lenta"
 * e "o que está devolvendo erro" sem nenhuma linha de instrumentação espalhada pelos
 * resources. Instrumentação escrita à mão em cada método vira instrumentação esquecida no
 * método novo — e o método novo é justamente o suspeito quando algo piora.</p>
 *
 * <p><b>POR QUE UM FILTRO, E NÃO UMA CHAMADA EM CADA RESOURCE.</b> São 40+ métodos de
 * resource hoje. Instrumentar um a um significaria 40 lugares para esquecer, e o primeiro
 * esquecido seria invisível: uma rota sem telemetria não aparece como zero no gráfico, ela
 * simplesmente <b>não existe</b> ali — e ninguém procura o que não sabe que falta.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O nome da operação vem do PADRÃO da rota, nunca da URL crua.</b>
 *       {@code /desastres/15320} e {@code /desastres/15321} são a mesma operação; contá-las
 *       separadas produziria uma linha de telemetria por evento do banco — 21.542 linhas
 *       para uma tela só, e o teto do coletor estourando em minutos.</li>
 *   <li><b>Status 4xx é RECUSA, 5xx é FALHA.</b> São coisas diferentes: 404 é o sistema
 *       funcionando (a pessoa pediu o que não existe); 500 é o sistema quebrado. Somá-los
 *       num contador de "erros" faria um rastreador varrendo URLs inexistentes parecer
 *       uma pane.</li>
 *   <li><b>Recursos estáticos NÃO são medidos.</b> CSS, JS e imagem inflariam a contagem
 *       com dezenas de requisições por página e afogariam as rotas que interessam.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer erro aqui é engolido pelo próprio
 * {@link Telemetria}, que nunca lança. Uma falha de medição não pode transformar uma
 * resposta 200 em 500.</p>
 */
@Provider
public class MedidorDeRequisicoes implements ContainerRequestFilter, ContainerResponseFilter {

    /** Onde o instante de início fica entre a ida e a volta da requisição. */
    private static final String INICIO = "org.nasa.telemetria.inicio";

    @Inject
    Telemetria telemetria;

    @Inject
    Relogio relogio;

    @Override
    public void filter(ContainerRequestContext requisicao) {
        requisicao.setProperty(INICIO, relogio.agora());
    }

    @Override
    public void filter(ContainerRequestContext requisicao, ContainerResponseContext resposta) {
        Object marcado = requisicao.getProperty(INICIO);
        if (!(marcado instanceof Instant inicio)) {
            // Sem o instante de ida nao ha o que medir. Acontece quando o filtro de
            // requisicao nao chegou a rodar — por exemplo numa falha de roteamento.
            return;
        }
        String rota = nomeDaRota(requisicao);
        if (rota == null) {
            return;
        }
        var duracao = Duration.between(inicio, relogio.agora());
        int status = resposta.getStatus();

        // 4xx e RECUSA (o sistema funcionando, o pedido e que estava errado);
        // 5xx e FALHA (o sistema quebrado). A distincao muda onde se investiga.
        if (status >= 500) {
            telemetria.falha(rota, duracao);
        } else if (status >= 400) {
            telemetria.recusa(rota, duracao);
        } else {
            telemetria.sucesso(rota, duracao);
        }
    }

    /**
     * O nome estável da rota.
     *
     * <p><b>Usa o PADRÃO declarado no resource</b> ({@code /desastres/{id}}), não o caminho
     * pedido ({@code /desastres/15320}). Sem isso, cada evento do banco viraria uma
     * operação distinta na telemetria.</p>
     *
     * @return {@code null} para o que não deve ser medido — estático e o próprio painel
     */
    private String nomeDaRota(ContainerRequestContext requisicao) {
        String caminho = requisicao.getUriInfo().getPath();
        if (caminho == null || caminho.isBlank()) {
            caminho = "/";
        }
        if (!caminho.startsWith("/")) {
            caminho = "/" + caminho;
        }

        // Estatico fora: dezenas de requisicoes por pagina afogariam as rotas que
        // interessam, e a latencia de servir um arquivo nao responde pergunta nenhuma
        // que este sistema tenha.
        if (caminho.startsWith("/estatico/") || caminho.startsWith("/paginas/")
                || caminho.startsWith("/q/") || caminho.equals("/favicon.ico")
                || caminho.equals("/robots.txt")) {
            return null;
        }

        // O padrao declarado no resource — `/desastres/{id}`, nao `/desastres/15320`.
        // A anotacao de template vem do JAX-RS quando ha correspondencia; sem ela,
        // cai no caminho normalizado abaixo.
        var uri = requisicao.getUriInfo();
        var casadas = uri.getMatchedURIs();
        if (!casadas.isEmpty()) {
            String maisEspecifico = casadas.get(0);
            if (!maisEspecifico.isBlank()) {
                caminho = "/" + maisEspecifico;
            }
        }

        // Ultima trava: qualquer segmento que seja so digitos vira `{id}`. Protege contra
        // o caso em que o JAX-RS devolve o caminho concreto — sem isto, um segmento
        // numerico produziria uma operacao por registro do banco.
        String normalizado = caminho.replaceAll("/\\d+(?=/|$)", "/{id}");

        return requisicao.getMethod() + " " + normalizado;
    }
}
