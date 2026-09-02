package org.nasa.cliente.domain.ports;

import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.Documento;

import java.util.List;
import java.util.Optional;

/**
 * Porta de saída: onde os clientes ficam guardados.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o contrato que permite aos casos de uso serem
 * escritos e testados <b>sem banco</b>. O adaptador é injetado; trocar SQLite por outra
 * coisa não toca em nenhuma regra.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nada aqui menciona SQL, tabela ou conexão.</b> Quem conhece isso é o adaptador
 *       em {@code infrastructure}.</li>
 *   <li><b>Ausência é {@link Optional#empty()}</b>, nunca nulo e nunca exceção: não achar
 *       é resultado normal de busca.</li>
 *   <li><b>{@link #salvar} devolve o cliente COM id e instante</b> — os dois só existem
 *       depois da gravação, e devolver o objeto de entrada faria o chamador seguir com um
 *       id nulo achando que gravou.</li>
 *   <li><b>A unicidade do documento é do BANCO.</b> {@link #existeComDocumento} serve
 *       para dar boa mensagem, não para proteger: entre a pergunta e a gravação cabe
 *       outra requisição.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Violação de unicidade vira
 * {@code DocumentoJaCadastradoException}; qualquer outra falha do banco vira exceção
 * específica do adaptador com causa-raiz. Nunca devolve lista vazia por erro — vazio
 * significa "procurei e não achei", e é isso que o painel vai contar.</p>
 */
public interface RepositorioDeClientesPort {

    /** Grava um cliente novo e devolve com id e instante preenchidos. */
    Cliente salvar(Cliente novo);

    /** Atualiza um cliente existente. */
    Cliente atualizar(Cliente existente);

    /** Remove pelo id. Devolve {@code false} se não havia o que remover. */
    boolean remover(long id);

    Optional<Cliente> porId(long id);

    Optional<Cliente> porDocumento(Documento documento);

    /** Já existe alguém com este documento? Só para mensagem — a garantia é do banco. */
    boolean existeComDocumento(Documento documento);

    /**
     * Página de clientes, ordenada por nome.
     *
     * <p><b>INVARIANTE:</b> ordenação <b>determinística</b> e limite obrigatório. Sem
     * ordem estável, a página 2 pode repetir ou pular registros da página 1; sem teto,
     * uma listagem carrega a base inteira na memória.</p>
     */
    List<Cliente> listar(int pagina, int tamanho);

    /** Busca por parte do nome ou do documento, com o mesmo teto da listagem. */
    List<Cliente> pesquisar(String termo, int pagina, int tamanho);

    /** Quantos clientes existem. Serve à paginação e à telemetria. */
    long contar();
}
