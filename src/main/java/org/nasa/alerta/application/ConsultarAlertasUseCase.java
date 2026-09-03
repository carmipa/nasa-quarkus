package org.nasa.alerta.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.SituacaoAlerta;
import org.nasa.alerta.domain.ports.EnvioDeAlertaPort;
import org.nasa.alerta.domain.ports.RepositorioDeAlertasPort;

import java.util.List;
import java.util.Optional;

/**
 * As leituras de alerta.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Responde "o que foi avisado, para quem, e o que falhou".
 * É a tela de auditoria do sistema — a que diz se a cobertura prometida existiu de fato.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Teto de {@value #TAMANHO_MAXIMO} por página.</b></li>
 *   <li><b>{@link #meioDeEntrega()} existe para a tela nunca mentir.</b> Enquanto o
 *       adaptador não entregar de verdade, quem olha o painel precisa ler isso <b>junto</b>
 *       com o "ENVIADO" — senão a tela mostra uma cobertura que não existe, e a descoberta
 *       vem no dia do desastre.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Consulta vazia é lista vazia — resposta
 * legítima, e a desejável na maior parte dos dias.</p>
 */
@ApplicationScoped
public class ConsultarAlertasUseCase {

    public static final int TAMANHO_MAXIMO = 100;

    @Inject
    RepositorioDeAlertasPort repositorio;

    @Inject
    EnvioDeAlertaPort envio;

    public List<Alerta> listar(int pagina, int tamanho) {
        return repositorio.listar(Math.max(0, pagina), limitar(tamanho));
    }

    public List<Alerta> porSituacao(String situacao, int pagina, int tamanho) {
        return repositorio.porSituacao(SituacaoAlerta.de(situacao),
                Math.max(0, pagina), limitar(tamanho));
    }

    public List<Alerta> doInscrito(long inscritoId, int pagina, int tamanho) {
        return repositorio.doInscrito(inscritoId, Math.max(0, pagina), limitar(tamanho));
    }

    public Optional<Alerta> porId(long id) {
        return repositorio.porId(id);
    }

    public List<RepositorioDeAlertasPort.ContagemPorSituacao> contarPorSituacao() {
        return repositorio.contarPorSituacao();
    }

    /** O que o sistema realmente usa para entregar — a tela mostra, para não mentir. */
    public MeioDeEntrega meioDeEntrega() {
        return new MeioDeEntrega(envio.descricaoDoMeio(), envio.entregaDeVerdade(),
                envio.entregaDeVerdade() ? null
                        : "os alertas sao REGISTRADOS mas NAO chegam a ninguem enquanto "
                          + "nao houver servidor de e-mail configurado");
    }

    /** Como os avisos saem, e se saem mesmo. */
    public record MeioDeEntrega(String descricao, boolean entregaDeVerdade, String ressalva) {
    }

    private static int limitar(int tamanho) {
        if (tamanho <= 0) {
            return 20;
        }
        return Math.min(tamanho, TAMANHO_MAXIMO);
    }
}
