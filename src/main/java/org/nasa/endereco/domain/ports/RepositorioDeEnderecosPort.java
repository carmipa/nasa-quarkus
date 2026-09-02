package org.nasa.endereco.domain.ports;

import org.nasa.endereco.domain.Endereco;

import java.util.List;
import java.util.Optional;

/**
 * O que a fatia de endereco precisa guardar e recuperar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Endereço gravado é o que torna o alerta possível: sem
 * ele o sistema sabe quem é o cliente e não sabe <b>onde</b> ele está, e a comparação com
 * o evento natural nunca acontece.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@link #comCoordenadaDoCliente(long)} devolve só os que TÊM coordenada.</b> É a
 *       consulta do alerta, e endereço sem coordenada não pode ser comparado com evento
 *       nenhum. Trazê-lo obrigaria quem chama a filtrar de novo — e um dia alguém
 *       esqueceria, comparando {@code null} com uma posição.</li>
 *   <li><b>A ligação com o cliente é a tabela de junção</b>, e é idempotente: vincular
 *       duas vezes não duplica.</li>
 *   <li><b>Coordenada é indivisível.</b> Ou os dois campos, ou nenhum — garantido pelo
 *       {@code CHECK} do esquema.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O adaptador traduz erro de banco em exceção da
 * fatia, com causa-raiz.</p>
 */
public interface RepositorioDeEnderecosPort {

    Endereco salvar(Endereco novo);

    Optional<Endereco> porId(long id);

    List<Endereco> listar(int pagina, int tamanho);

    List<Endereco> porCep(String digitos, int pagina, int tamanho);

    boolean remover(long id);

    void vincularAoCliente(long enderecoId, long clienteId);

    void desvincularDoCliente(long enderecoId, long clienteId);

    List<Endereco> doCliente(long clienteId);

    /**
     * Os endereços do cliente que <b>têm coordenada</b> — a consulta do alerta.
     *
     * <p>Endereço sem coordenada nunca entra: não há o que comparar com a posição do
     * evento, e trazê-lo empurraria a decisão para quem chama.</p>
     */
    List<Endereco> comCoordenadaDoCliente(long clienteId);

    /**
     * Todos os clientes que têm ao menos um endereço com coordenada.
     *
     * <p>É por onde a varredura de alerta começa: percorrer clientes sem endereço
     * localizável seria trabalho garantido a não produzir nada.</p>
     */
    List<Long> clientesComEnderecoLocalizavel();

    long contar();
}
