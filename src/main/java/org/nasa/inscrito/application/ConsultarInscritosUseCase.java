package org.nasa.inscrito.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.core.tempo.Relogio;
import org.nasa.inscrito.domain.Inscrito;
import org.nasa.inscrito.domain.exceptions.InscricaoNaoEncontradaException;
import org.nasa.inscrito.domain.ports.RepositorioDeInscritosPort;

import java.util.List;

/**
 * Lê as inscrições — para a tela e para a varredura de alertas.
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O tamanho da página tem TETO.</b> Sem ele, {@code ?tamanho=1000000} traria a
 *       base inteira pela rede porque alguém digitou um número na URL.</li>
 *   <li><b>Quem não tem coordenada é CONTADO e mostrado.</b> Ele existe e não recebe alerta
 *       de proximidade; esconder isso faria alguém esperar um aviso que nunca vem.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Id inexistente vira
 * {@link InscricaoNaoEncontradaException}, que a borda traduz em 404 — nunca uma tela em
 * branco que parece inscrição sem dado.</p>
 */
@ApplicationScoped
public class ConsultarInscritosUseCase {

    /** Teto do tamanho de página. Acima disso não se lê, se rola. */
    public static final int TAMANHO_MAXIMO = 100;

    /** Teto de quem a varredura de alertas considera numa passada. */
    public static final int MAXIMO_ALCANCAVEIS = 5_000;

    @Inject
    RepositorioDeInscritosPort repositorio;

    @Inject
    Relogio relogio;

    public List<Inscrito> listar(int pagina, int tamanho) {
        return repositorio.listar(Math.max(0, pagina), limitar(tamanho));
    }

    public List<Inscrito> alcancaveis() {
        return repositorio.alcancaveis(MAXIMO_ALCANCAVEIS);
    }

    public Inscrito exigirPorId(long id) {
        return repositorio.porId(id)
                .orElseThrow(() -> new InscricaoNaoEncontradaException(String.valueOf(id)));
    }

    public boolean cancelar(long id) {
        return repositorio.cancelar(id, relogio.agora());
    }

    public long contar() {
        return repositorio.contar();
    }

    public long contarAtivos() {
        return repositorio.contarAtivos();
    }

    public long contarSemCoordenada() {
        return repositorio.contarSemCoordenada();
    }

    private static int limitar(int tamanho) {
        if (tamanho <= 0) {
            return 20;
        }
        return Math.min(tamanho, TAMANHO_MAXIMO);
    }
}
