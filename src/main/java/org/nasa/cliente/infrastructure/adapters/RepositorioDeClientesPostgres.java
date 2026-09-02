package org.nasa.cliente.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.Documento;
import org.nasa.cliente.domain.exceptions.DocumentoJaCadastradoException;
import org.nasa.cliente.domain.ports.RepositorioDeClientesPort;
import org.nasa.core.tempo.Relogio;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * O cadastro de clientes no PostgreSQL.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o único lugar do sistema que sabe como um cliente vira
 * linha de tabela. Toda regra vive nos casos de uso; aqui só há tradução.</p>
 *
 * <p><b>O QUE MUDOU NA PORTABILIDADE DO SQLITE</b> (02/09/2026), porque duas destas
 * diferenças não dão erro nenhum — mudam o comportamento em silêncio:</p>
 * <ol>
 *   <li><b>{@code LIKE} virou {@code ILIKE}.</b> No SQLite o {@code LIKE} é insensível a
 *       maiúsculas para ASCII; no PostgreSQL <b>não é</b>. Portar literalmente faria a
 *       pesquisa por {@code bruno} parar de encontrar "Bruno" — sem exceção, sem log, só
 *       resultado vazio que parece "não existe".</li>
 *   <li><b>Data e instante deixaram de trafegar como texto.</b> Eram {@code setString} com
 *       ISO-8601 porque o SQLite não tinha os tipos; agora são {@code LocalDate} e
 *       {@code OffsetDateTime} em UTC explícito. Nunca {@code LocalDateTime}: esse tipo não
 *       carrega fuso, e o driver o interpretaria no fuso da sessão — reintroduzindo, pela
 *       porta dos fundos, o mesmo defeito de fuso corrigido no log nesta mesma data.</li>
 *   <li><b>A duplicata é detectada por {@code SQLSTATE 23505} e pelo NOME da restrição</b>,
 *       não por procurar a palavra "UNIQUE" na mensagem. Mensagem de erro é texto do
 *       fornecedor: muda de versão para versão, e é traduzida conforme o idioma do
 *       servidor.</li>
 *   <li><b>{@code RETURNING id}</b> no lugar de {@code getGeneratedKeys()}. A versão antiga
 *       devolvia {@code 0L} calada quando a chave não vinha, e esse zero iria para o
 *       cabeçalho {@code Location} apontando um recurso que não existe.</li>
 * </ol>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Consulta sempre parametrizada.</b> Nenhum valor entra no SQL por concatenação —
 *       inclusive na pesquisa por texto, que é justamente onde a tentação aparece.</li>
 *   <li><b>Ordenação determinística com desempate por {@code id}.</b> Ordenar só por nome
 *       faz dois homônimos trocarem de lugar entre uma página e outra, e a paginação passa
 *       a repetir um e pular o outro — sem erro nenhum.</li>
 *   <li><b>Instante em UTC</b>, vindo do relógio injetado, gravado em {@code TIMESTAMPTZ}.</li>
 *   <li><b>Violação de unicidade é traduzida</b> para {@link DocumentoJaCadastradoException}:
 *       o operador precisa ler "esta pessoa já está cadastrada", não o código do banco.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Erro de banco vira
 * {@link FalhaNoCadastroDeClientesException} com causa-raiz e a operação no alvo. A violação
 * de unicidade do documento é a única traduzida para exceção de negócio — porque é a única
 * que o operador consegue resolver sozinho.</p>
 */
@ApplicationScoped
public class RepositorioDeClientesPostgres implements RepositorioDeClientesPort {

    private static final String COLUNAS =
            "id, nome, sobrenome, data_nascimento, documento, criado_em";

    /** SQLSTATE padrão de violação de unicidade. Não é texto do fornecedor. */
    private static final String UNIQUE_VIOLATION = "23505";

    /** Nome DECLARADO da restrição, como está na V001. */
    private static final String RESTRICAO_DOCUMENTO = "cliente_documento_unico";

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public Cliente salvar(Cliente novo) {
        String sql = "INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id";
        Instant agora = relogio.agora();
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, novo.nome());
            ps.setString(2, novo.sobrenome());
            ps.setObject(3, novo.dataNascimento());
            ps.setString(4, novo.documento().digitos());
            ps.setObject(5, agora.atOffset(ZoneOffset.UTC));

            try (ResultSet chaves = ps.executeQuery()) {
                if (!chaves.next()) {
                    // Insercao sem chave devolvida nao deveria acontecer com RETURNING.
                    // Se acontecer, falhar aqui e melhor que devolver id 0 — que iria no
                    // cabecalho Location apontando um recurso inexistente.
                    throw new FalhaNoCadastroDeClientesException("salvar",
                            novo.documento().digitos(), null);
                }
                return new Cliente(chaves.getLong("id"), novo.nome(), novo.sobrenome(),
                        novo.dataNascimento(), novo.documento(), agora);
            }
        } catch (SQLException e) {
            throw traduzir(e, "salvar", novo.documento().digitos());
        }
    }

    @Override
    public Cliente atualizar(Cliente existente) {
        String sql = "UPDATE cliente SET nome = ?, sobrenome = ?, data_nascimento = ?, "
                + "documento = ? WHERE id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, existente.nome());
            ps.setString(2, existente.sobrenome());
            ps.setObject(3, existente.dataNascimento());
            ps.setString(4, existente.documento().digitos());
            ps.setLong(5, existente.id());
            ps.executeUpdate();
            return existente;
        } catch (SQLException e) {
            throw traduzir(e, "atualizar", String.valueOf(existente.id()));
        }
    }

    @Override
    public boolean remover(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement("DELETE FROM cliente WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traduzir(e, "remover", String.valueOf(id));
        }
    }

    @Override
    public Optional<Cliente> porId(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM cliente WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw traduzir(e, "buscar-por-id", String.valueOf(id));
        }
    }

    @Override
    public Optional<Cliente> porDocumento(Documento documento) {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM cliente WHERE documento = ?")) {
            ps.setString(1, documento.digitos());
            return primeiro(ps);
        } catch (SQLException e) {
            throw traduzir(e, "buscar-por-documento", documento.digitos());
        }
    }

    @Override
    public boolean existeComDocumento(Documento documento) {
        return porDocumento(documento).isPresent();
    }

    @Override
    public List<Cliente> listar(int pagina, int tamanho) {
        // Desempate por id: sem ele, homonimos trocam de lugar entre paginas e a
        // paginacao repete um e pula outro, sem erro nenhum.
        String sql = "SELECT " + COLUNAS + " FROM cliente ORDER BY nome, sobrenome, id "
                + "LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "listar", "pagina=" + pagina);
        }
    }

    @Override
    public List<Cliente> pesquisar(String termo, int pagina, int tamanho) {
        // ILIKE, e nao LIKE: no PostgreSQL o LIKE e SENSIVEL a maiusculas, ao contrario
        // do SQLite. Portar literalmente faria `bruno` deixar de achar "Bruno" calado.
        // `documento` segue com LIKE porque so tem digitos, onde caixa nao existe.
        String sql = "SELECT " + COLUNAS + " FROM cliente "
                + "WHERE (? AND (nome ILIKE ? OR sobrenome ILIKE ?)) "
                + "   OR (? AND documento LIKE ?) "
                + "ORDER BY nome, sobrenome, id LIMIT ? OFFSET ?";
        // CADA METADE DO FILTRO SO VALE SE TIVER O QUE PROCURAR, e este guarda
        // conserta um defeito REAL medido em 02/09/2026: sem ele, um termo sem
        // digitos produzia `soDigitos = "%%"`, e `documento LIKE '%%'` casa com
        // TODA linha da tabela. Pesquisar "zzzzzz" devolvia a base inteira — a
        // caixa de busca aceitava o texto, respondia rapido, e simplesmente nao
        // filtrava. Veio do adaptador SQLite e foi portada fielmente, ate a tela
        // de lista tornar o sintoma visivel.
        //
        // O termo e PARAMETRO, nunca concatenado: e aqui que a injecao de SQL entraria.
        // A limpeza tambem remove `%` e `_`, que sao curingas DENTRO do padrao — sem
        // isso, quem digitasse `%` listaria a base inteira.
        String texto = termo.replaceAll("[^\\p{L}\\p{N} ]", "").strip();
        String digitos = termo.replaceAll("[^0-9]", "");
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, !texto.isEmpty());
            ps.setString(2, "%" + texto + "%");
            ps.setString(3, "%" + texto + "%");
            ps.setBoolean(4, !digitos.isEmpty());
            ps.setString(5, "%" + digitos + "%");
            ps.setInt(6, tamanho);
            ps.setInt(7, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "pesquisar", termo);
        }
    }

    @Override
    public long contar() {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM cliente")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw traduzir(e, "contar", "cliente");
        }
    }

    // ------------------------------------------------------------------ apoio

    private static Optional<Cliente> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
        }
    }

    private static List<Cliente> todos(PreparedStatement ps) throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    /**
     * Traduz a linha em cliente.
     *
     * <p>Lê {@code criado_em} como {@link OffsetDateTime} e não como
     * {@code LocalDateTime}: o segundo não carrega fuso, e o driver o interpretaria no
     * fuso da sessão — que é o mesmo tipo de defeito corrigido no log em 02/09.</p>
     */
    private static Cliente daLinha(ResultSet rs) throws SQLException {
        return new Cliente(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("sobrenome"),
                rs.getObject("data_nascimento", LocalDate.class),
                new Documento(rs.getString("documento")),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant());
    }

    /**
     * Traduz o erro do banco para a linguagem da fatia.
     *
     * <p>A violação de unicidade do documento é a única que vira exceção de <b>negócio</b>:
     * é a única que o operador resolve sozinho. As demais são falha de infraestrutura e
     * sobem como tal, com causa-raiz.</p>
     *
     * <p>O reconhecimento usa o {@code SQLSTATE} padrão mais o <b>nome</b> da restrição.
     * Um {@code 23505} de outra restrição não é traduzido de propósito: dizer "documento
     * já cadastrado" para uma duplicata de e-mail mandaria o operador corrigir o campo
     * errado.</p>
     */
    private static RuntimeException traduzir(SQLException e, String operacao, String alvo) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            String texto = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (texto.contains(RESTRICAO_DOCUMENTO)) {
                return new DocumentoJaCadastradoException(alvo);
            }
        }
        return new FalhaNoCadastroDeClientesException(operacao, alvo, e);
    }
}
