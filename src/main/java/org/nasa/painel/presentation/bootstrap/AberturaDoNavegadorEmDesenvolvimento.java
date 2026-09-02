package org.nasa.painel.presentation.bootstrap;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

/**
 * Abre o navegador na home quando a aplicação sobe — <b>só em desenvolvimento</b>.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Comodidade de quem trabalha: subir o projeto e já ver a
 * tela, sem copiar a URL do console a cada reinício. O Quarkus tem a tecla {@code w} no
 * console de dev para isso, mas ela exige o console em foco — e o ciclo de trabalho é
 * salvar, alternar para o navegador e recarregar.</p>
 *
 * <p><b>ISTO NUNCA PODE ACONTECER EM PRODUÇÃO, e é o ponto principal desta classe.</b> O
 * destino é uma VPS sem tela, rodando junto com outros nove serviços. Um processo de
 * servidor tentando abrir navegador ali é, na melhor hipótese, uma exceção no arranque; na
 * pior, um processo pendurado esperando algo que nunca vai existir. Por isso há
 * <b>quatro</b> travas independentes, e não uma:</p>
 * <ol>
 *   <li><b>{@link IfBuildProfile}{@code ("dev")}</b> — em produção esta classe sequer entra
 *       no bean container. É a trava que vale, porque acontece em tempo de build: o código
 *       não existe no jar de produção;</li>
 *   <li><b>ambiente gráfico</b> — {@link GraphicsEnvironment#isHeadless()} recusa em
 *       qualquer máquina sem tela, inclusive um contêiner de desenvolvimento;</li>
 *   <li><b>{@link Desktop#isDesktopSupported()}</b> — recusa onde a API não existe;</li>
 *   <li><b>{@code nasa.dev.abrir-navegador}</b> — desligável por quem não quiser, sem
 *       tocar em código.</li>
 * </ol>
 * <p>Uma trava só seria suficiente <b>se nada mudasse</b>. Quatro sobrevivem a alguém
 * empacotar em outro perfil por engano — que é exatamente o tipo de engano que acontece
 * no dia do deploy.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Falha ABERTA, e isso é deliberado.</b> Se o navegador não abrir, a aplicação
 *       sobe do mesmo jeito e o log diz por quê. Conforto de desenvolvimento nunca pode
 *       derrubar o arranque — seria trocar um incômodo por um impedimento.</li>
 *   <li><b>Abre em uma linha separada de execução.</b> {@link Desktop#browse(URI)} bloqueia
 *       até o sistema operacional responder, e no Windows isso leva um instante
 *       perceptível; segurar o arranque por causa disso atrasaria o recarregamento a cada
 *       alteração de código.</li>
 *   <li><b>A porta vem da configuração</b>, nunca fixa no código: quem sobe numa porta
 *       diferente veria o navegador abrir na porta errada, que é pior que não abrir.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer erro é registrado em INFO com o
 * motivo e engolido ali. Não há exceção específica de propósito: esta é a única parte do
 * sistema em que o fracasso <b>não</b> tem consequência — a URL continua no console, a um
 * clique de distância.</p>
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class AberturaDoNavegadorEmDesenvolvimento {

    private static final Logger LOG =
            Logger.getLogger(AberturaDoNavegadorEmDesenvolvimento.class);
    private static final String OPERACAO = "abrir-navegador";

    @ConfigProperty(name = "nasa.dev.abrir-navegador", defaultValue = "true")
    boolean habilitado;

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int porta;

    void aoIniciar(@Observes StartupEvent evento) {
        if (!habilitado) {
            LOG.info(Registro.recusa(OPERACAO, "config", "DESLIGADO_POR_CONFIGURACAO"));
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            // Contêiner de desenvolvimento, sessão SSH, máquina sem tela.
            LOG.info(Registro.recusa(OPERACAO, "ambiente", "SEM_AMBIENTE_GRAFICO"));
            return;
        }
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            LOG.info(Registro.recusa(OPERACAO, "ambiente", "DESKTOP_BROWSE_INDISPONIVEL"));
            return;
        }

        String url = "http://localhost:" + porta + "/";

        // Linha separada: `browse` bloqueia ate o sistema responder, e no Windows isso
        // atrasaria cada recarregamento de codigo em modo dev.
        Thread abridor = new Thread(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
                LOG.info(Registro.de(OPERACAO, url, "navegador aberto"));
            } catch (Exception naoAbriu) {
                // FALHA ABERTA de proposito: conforto de desenvolvimento nao derruba
                // arranque. A URL continua no console.
                LOG.info(Registro.recusa(OPERACAO, url,
                        "NAO_ABRIU_" + naoAbriu.getClass().getSimpleName()));
            }
        }, "abrir-navegador-dev");
        abridor.setDaemon(true);   // nunca segura o encerramento da aplicacao
        abridor.start();
    }
}
