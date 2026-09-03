package org.nasa.inscrito.domain.ports;

import org.nasa.inscrito.domain.Email;
import org.nasa.inscrito.domain.Inscrito;

import java.util.List;
import java.util.Optional;

/**
 * O que a fatia de inscrição precisa guardar e recuperar.
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Um e-mail se inscreve UMA VEZ</b>, e a garantia é do BANCO — não de uma
 *       checagem daqui, que não sobreviveria a dois cliques simultâneos no botão. É o erro
 *       de boa-fé mais comum que existe num formulário: clicar duas vezes porque a página
 *       demorou. Sem a restrição, a pessoa passaria a receber cada alerta em dobro.</li>
 *   <li><b>Cancelar NÃO apaga.</b> A inscrição sai dos alertas e o histórico do que já foi
 *       enviado continua fazendo sentido. Apagar deixaria alertas apontando para ninguém —
 *       e a auditoria de "quem foi avisado sobre este evento" ficaria com buracos.</li>
 *   <li><b>Toda listagem é paginada</b>, exceto a que alimenta a varredura de alertas, que
 *       tem limite próprio.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O adaptador traduz erro de banco em exceção da
 * fatia, com causa-raiz — nunca deixa {@code SQLException} vazar.</p>
 */
public interface RepositorioDeInscritosPort {

    /**
     * Grava uma inscrição nova.
     *
     * @return a inscrição com o id que o banco atribuiu
     * @throws org.nasa.inscrito.domain.exceptions.EmailJaInscritoException quando o e-mail
     *         já está inscrito. É recusa, não falha: a pessoa já está na lista
     */
    Inscrito gravar(Inscrito inscrito);

    /** Atualiza a coordenada de quem foi inscrito sem ela — a geocodificação que chegou depois. */
    void atualizarCoordenada(long id, double latitude, double longitude);

    /**
     * Marca como cancelada.
     *
     * @return {@code true} se alguma linha mudou. {@code false} significa que ela já estava
     *         cancelada ou não existe — e quem chama decide, em vez de receber uma exceção
     *         por cancelar duas vezes, que é o clique repetido de sempre
     */
    boolean cancelar(long id, java.time.Instant agora);

    Optional<Inscrito> porId(long id);

    Optional<Inscrito> porEmail(Email email);

    List<Inscrito> listar(int pagina, int tamanho);

    /**
     * Quem pode receber alerta de proximidade: <b>ativo e com coordenada</b>.
     *
     * <p>Sem coordenada não há o que comparar com a posição do desastre — e mandar alerta
     * "por via das dúvidas" para quem o sistema não sabe localizar treinaria a pessoa a
     * ignorar o aviso.</p>
     */
    List<Inscrito> alcancaveis(int limite);

    long contar();

    long contarAtivos();

    /** Quantos estão ativos mas SEM coordenada — a tela mostra isto, nunca esconde. */
    long contarSemCoordenada();
}
