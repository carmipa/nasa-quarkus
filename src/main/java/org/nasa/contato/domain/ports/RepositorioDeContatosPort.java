package org.nasa.contato.domain.ports;

import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.Email;
import org.nasa.contato.domain.TipoContato;

import java.util.List;
import java.util.Optional;

/**
 * O que a fatia de contato precisa guardar e recuperar — sem dizer onde.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a fronteira que permite aos casos de uso serem
 * escritos e testados <b>sem banco</b>. O adaptador é injetado; trocar PostgreSQL por
 * outra coisa não toca em regra nenhuma — foi exatamente o que se comprovou em 02/09,
 * quando a troca de SQLite por PostgreSQL custou quatro arquivos e nenhuma linha de
 * domínio.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nenhum método devolve {@code null}.</b> Ausência é {@link Optional#empty()} ou
 *       lista vazia. {@code null} de repositório vira NullPointerException longe da
 *       causa, num lugar onde ninguém procura pela consulta.</li>
 *   <li><b>Toda listagem é paginada.</b> Não existe "traga tudo": a base cresce, e o
 *       método que hoje devolve doze registros é o que amanhã carrega a memória inteira
 *       — sem nunca ter mudado.</li>
 *   <li><b>{@link #deEmergenciaDoCliente(long)} é a consulta do alerta</b>, e por isso
 *       não é paginada: quem vai ser avisado tem de ser avisado <b>inteiro</b>. Paginar
 *       aqui significaria avisar a primeira página de contatos e esquecer o resto,
 *       silenciosamente.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O adaptador traduz erro de banco para
 * exceção da fatia: duplicata de e-mail vira {@code EmailJaCadastradoException}
 * (resolvível por quem opera), o resto vira {@code FalhaNoCadastroDeContatosException}.</p>
 */
public interface RepositorioDeContatosPort {

    Contato salvar(Contato novo);

    Contato atualizar(Contato existente);

    boolean remover(long id);

    Optional<Contato> porId(long id);

    Optional<Contato> porEmail(Email email);

    List<Contato> listar(int pagina, int tamanho);

    /** Busca aproximada por e-mail ou por número de telefone. */
    List<Contato> pesquisar(String termo, int pagina, int tamanho);

    List<Contato> porTipo(TipoContato tipo, int pagina, int tamanho);

    /**
     * Os contatos de emergência de um cliente — <b>todos</b>, sem paginação.
     *
     * <p>É a consulta que decide quem recebe o alerta de desastre. Paginar aqui avisaria
     * a primeira página de pessoas e esqueceria as demais, sem erro nenhum.</p>
     */
    List<Contato> deEmergenciaDoCliente(long clienteId);

    /** Liga um contato a um cliente. Idempotente: ligar duas vezes não duplica. */
    void vincularAoCliente(long contatoId, long clienteId);

    void desvincularDoCliente(long contatoId, long clienteId);

    List<Contato> doCliente(long clienteId);

    long contar();
}
