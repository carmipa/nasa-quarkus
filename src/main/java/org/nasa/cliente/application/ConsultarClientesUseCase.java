package org.nasa.cliente.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.Documento;
import org.nasa.cliente.domain.exceptions.ClienteNaoEncontradoException;
import org.nasa.cliente.domain.ports.RepositorioDeClientesPort;

import java.util.List;
import java.util.Optional;

/**
 * As leituras do cadastro: listar, detalhar, achar por documento e pesquisar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É por aqui que quem opera encontra a pessoa antes de
 * qualquer ação sobre ela — e é a operação mais frequente do sistema, de longe.</p>
 *
 * <p><b>POR QUE AS QUATRO LEITURAS NUM CASO DE USO SÓ.</b> Elas compartilham as mesmas
 * regras de paginação e o mesmo teto. Separá-las em quatro classes duplicaria essas
 * regras em quatro lugares — e regra duplicada diverge, que é o defeito que esta
 * arquitetura existe para evitar. Escrita é outra história: cada uma tem invariante
 * própria e mora no seu próprio caso de uso.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Teto obrigatório de página.</b> Pedido acima do teto é <b>grampeado</b>, não
 *       recusado — mas o teto é aplicado de verdade. Sem ele, {@code ?tamanho=1000000}
 *       carrega a base inteira na memória, e a lentidão aparece longe da causa.</li>
 *   <li><b>Página negativa vira a primeira</b>, em vez de estourar. É erro de digitação
 *       ou de link antigo, não motivo para tela de erro.</li>
 *   <li><b>Ausência é {@link Optional#empty()}</b> nas buscas que podem não achar;
 *       {@link #exigirPorId} é a variante para quem <b>precisa</b> do cliente e deve
 *       falhar com 404 se ele não existir.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não lança em consulta: devolve vazio. A
 * única exceção é {@link #exigirPorId}, e ela é explícita no nome justamente para que
 * ninguém a chame por engano num caminho que tolera ausência.</p>
 */
@ApplicationScoped
public class ConsultarClientesUseCase {

    /** Teto de itens por página. Acima disto a consulta deixa de ser consulta. */
    public static final int TAMANHO_MAXIMO = 100;
    public static final int TAMANHO_PADRAO = 20;

    @Inject
    RepositorioDeClientesPort repositorio;

    public List<Cliente> listar(int pagina, int tamanho) {
        return repositorio.listar(paginaValida(pagina), tamanhoValido(tamanho));
    }

    public List<Cliente> pesquisar(String termo, int pagina, int tamanho) {
        if (termo == null || termo.isBlank()) {
            // Pesquisa sem termo é a listagem — e dizer isso é melhor que devolver vazio,
            // que o operador leria como "não existe ninguém".
            return listar(pagina, tamanho);
        }
        return repositorio.pesquisar(termo.trim(), paginaValida(pagina), tamanhoValido(tamanho));
    }

    public Optional<Cliente> porId(long id) {
        return repositorio.porId(id);
    }

    public Optional<Cliente> porDocumento(String documento) {
        return repositorio.porDocumento(new Documento(documento));
    }

    /** Para quem PRECISA do cliente: ausência é {@link ClienteNaoEncontradoException}. */
    public Cliente exigirPorId(long id) {
        return repositorio.porId(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("id=" + id));
    }

    public long contar() {
        return repositorio.contar();
    }

    private static int paginaValida(int pagina) {
        return Math.max(0, pagina);
    }

    private static int tamanhoValido(int tamanho) {
        if (tamanho <= 0) {
            return TAMANHO_PADRAO;
        }
        return Math.min(tamanho, TAMANHO_MAXIMO);
    }
}
