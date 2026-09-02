package org.nasa.contato.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.Email;
import org.nasa.contato.domain.TipoContato;
import org.nasa.contato.domain.exceptions.ContatoNaoEncontradoException;
import org.nasa.contato.domain.ports.RepositorioDeContatosPort;

import java.util.List;
import java.util.Optional;

/**
 * Todas as leituras de contato.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Reúne num lugar só as consultas que a tela e a API
 * fazem, para que o teto de paginação seja UM e não uma decisão repetida em cada chamada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Teto de {@value #TAMANHO_MAXIMO} por página, sempre.</b> Pedir um milhão não
 *       carrega a base inteira na memória — e a lentidão que isso causaria apareceria
 *       longe da causa, num servidor que "às vezes fica pesado".</li>
 *   <li><b>Página negativa vira zero.</b> Recusar seria rigor sem propósito: o resultado
 *       desejado é óbvio, e um 400 aqui só transformaria um engano de URL em erro.</li>
 *   <li><b>{@link #deEmergenciaDoCliente(long)} NÃO é paginada.</b> É a consulta que
 *       decide quem recebe alerta de desastre, e paginar avisaria a primeira página de
 *       pessoas esquecendo as demais, sem erro nenhum.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Consulta que responde vazio devolve
 * {@link Optional#empty()} ou lista vazia — nunca exceção, porque "não existe" é resposta
 * legítima. {@link #exigirPorId(long)} é a exceção declarada: existe para os caminhos em
 * que a ausência JÁ é erro, como abrir a tela de um contato apagado.</p>
 */
@ApplicationScoped
public class ConsultarContatosUseCase {

    /** Teto de itens por página. Vale para tela e para API. */
    public static final int TAMANHO_MAXIMO = 100;

    @Inject
    RepositorioDeContatosPort repositorio;

    public List<Contato> listar(int pagina, int tamanho) {
        return repositorio.listar(Math.max(0, pagina), limitar(tamanho));
    }

    public List<Contato> pesquisar(String termo, int pagina, int tamanho) {
        return repositorio.pesquisar(termo == null ? "" : termo,
                Math.max(0, pagina), limitar(tamanho));
    }

    public List<Contato> porTipo(String tipo, int pagina, int tamanho) {
        return repositorio.porTipo(TipoContato.de(tipo), Math.max(0, pagina), limitar(tamanho));
    }

    public Optional<Contato> porId(long id) {
        return repositorio.porId(id);
    }

    public Optional<Contato> porEmail(String email) {
        return repositorio.porEmail(new Email(email));
    }

    /** Para os caminhos em que a ausência já é erro — abrir a tela de um contato apagado. */
    public Contato exigirPorId(long id) {
        return repositorio.porId(id)
                .orElseThrow(() -> new ContatoNaoEncontradoException(String.valueOf(id)));
    }

    public List<Contato> doCliente(long clienteId) {
        return repositorio.doCliente(clienteId);
    }

    /** Quem recebe o alerta de desastre deste cliente. Inteiro, sem paginação. */
    public List<Contato> deEmergenciaDoCliente(long clienteId) {
        return repositorio.deEmergenciaDoCliente(clienteId);
    }

    public long contar() {
        return repositorio.contar();
    }

    private static int limitar(int tamanho) {
        if (tamanho <= 0) {
            return 10;
        }
        return Math.min(tamanho, TAMANHO_MAXIMO);
    }
}
