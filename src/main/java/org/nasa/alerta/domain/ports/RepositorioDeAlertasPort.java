package org.nasa.alerta.domain.ports;

import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.SituacaoAlerta;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * O que a fatia de alerta guarda, recupera e — o mais importante — descobre.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Duas responsabilidades bem diferentes moram aqui: a
 * caixa de saída (registrar, listar pendentes, marcar concluído) e o <b>modelo de
 * leitura</b> que responde "quem está perto de quê".</p>
 *
 * <p><b>POR QUE O MODELO DE LEITURA É SQL PRÓPRIO, e não uma chamada às outras fatias:</b>
 * a regra é dura e está certa — <b>fatia não conhece fatia</b>. Esta fatia monta o que
 * precisa com consultas sobre o esquema, que é compartilhado e pertence ao peer
 * {@code persistencia}. Se o cadastro de cliente mudar de forma amanhã, o alerta continua
 * compilando e só uma consulta muda — em vez de uma cascata por quatro fatias.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@link #registrarSeNovo(Alerta)} é idempotente pelo BANCO</b>, na chave
 *       {@code (cliente_id, evento_id)}. Uma tempestade que dura cinco dias aparece em
 *       cinco varreduras; sem esta chave, são cinco avisos para a mesma pessoa.</li>
 *   <li><b>{@link #candidatos(double, Instant, int)} é FILTRO GROSSEIRO.</b> Devolve pares
 *       próximos em graus; quem decide a distância é a geodésia, no caso de uso. Grau não
 *       é quilômetro, e a conversão varia com a latitude.</li>
 *   <li><b>Pendentes vêm em ordem de chegada.</b> O aviso mais antigo é o que está
 *       esperando há mais tempo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O adaptador traduz erro de banco em exceção da
 * fatia, com causa-raiz.</p>
 */
public interface RepositorioDeAlertasPort {

    /**
     * Registra o aviso, se ele ainda nao existir para aquele par cliente/evento.
     *
     * @return {@code true} se registrou agora; {@code false} se já existia — que é
     *         resultado NORMAL e não erro, porque a varredura repete
     */
    boolean registrarSeNovo(Alerta novo);

    List<Alerta> pendentes(int limite);

    Alerta atualizar(Alerta alerta);

    Optional<Alerta> porId(long id);

    List<Alerta> listar(int pagina, int tamanho);

    List<Alerta> porSituacao(SituacaoAlerta situacao, int pagina, int tamanho);

    List<Alerta> doInscrito(long inscritoId, int pagina, int tamanho);

    List<ContagemPorSituacao> contarPorSituacao();

    record ContagemPorSituacao(String situacao, long quantos) {
    }

    /**
     * Os pares (endereco de cliente, evento ativo) proximos em GRAUS.
     *
     * <p><b>Filtro grosseiro, e deliberado.</b> A comparação é feita em graus, com uma
     * conversão aproximada que o próprio SQL faz; ela serve para o banco usar índice e
     * reduzir o conjunto. A distância real, em quilômetros sobre a esfera, é calculada
     * depois pela geodésia — e é ela que decide quem entra.</p>
     *
     * @param raioKm  o raio pretendido, usado só para dimensionar o recorte grosseiro
     * @param desde   janela: eventos mais antigos que isto não são considerados
     * @param limite  teto de pares, para proteger a memória
     */
    List<Candidato> candidatos(double raioKm, Instant desde, int limite);

    /**
     * Um par que PODE virar alerta — falta a geodesia decidir.
     *
     * <p>Traz as coordenadas cruas dos dois lados de propósito: quem calcula a distância
     * é o caso de uso, com a mesma geodésia que o resto do sistema usa. Um segundo
     * cálculo, em SQL, seria um segundo lugar para divergir.</p>
     */
    record Candidato(long inscritoId, String nomeInscrito, String destino,
                     long eventoId, String eventoTitulo,
                     double latitudeInscrito, double longitudeInscrito,
                     double latitudeEvento, double longitudeEvento) {
    }
}
