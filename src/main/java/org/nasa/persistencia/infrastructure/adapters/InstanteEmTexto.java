package org.nasa.persistencia.infrastructure.adapters;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Converte instante em texto e de volta — o que substituiu o {@code TIMESTAMPTZ}.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O SQLite <b>não tem tipo de data</b>. Ele aceita qualquer
 * coisa em qualquer coluna, e nada impede que um lugar do código grave em hora local
 * enquanto outro grava em UTC. No PostgreSQL o tipo da coluna resolvia isso sozinho; aqui
 * a garantia precisa ser construída — e ela é construída em <b>dois lugares</b>:</p>
 *
 * <ol>
 *   <li><b>aqui</b>, que é o único caminho por onde instante vira texto no projeto;</li>
 *   <li><b>no esquema</b>, com {@code CHECK (coluna LIKE '%Z')} em toda coluna de instante.
 *       Uma gravação em hora local é <b>recusada pelo banco</b>, não aceita em silêncio.</li>
 * </ol>
 *
 * <p>Os dois juntos mantêm o UTC como <b>mecanismo</b>, e não como convenção que alguém
 * precisa lembrar. É a diferença que o log em {@code -03:00} já custou a este projeto.</p>
 *
 * <p><b>O FORMATO, E POR QUE ELE É ESTE.</b> {@code 2026-09-03T01:23:45Z} — sempre 20
 * caracteres, sempre UTC, sempre com o {@code Z}. Três propriedades vêm de graça:</p>
 * <ul>
 *   <li><b>a ordem alfabética é a cronológica</b>, porque a largura é fixa e os campos vão
 *       do mais significativo ao menos. {@code ORDER BY}, {@code MIN} e {@code MAX}
 *       continuam corretos sem função de data alguma;</li>
 *   <li><b>{@code substr(x, 1, 4)} é o ano e {@code substr(x, 1, 10)} é o dia</b>, ambos já
 *       em UTC. Foi assim que {@code EXTRACT(YEAR FROM ... AT TIME ZONE 'UTC')} sumiu sem
 *       perder nada;</li>
 *   <li><b>comparar com {@code >=} funciona</b>, então janelas de tempo são texto contra
 *       texto.</li>
 * </ul>
 *
 * <p><b>POR QUE TRUNCA EM SEGUNDOS.</b> A largura precisa ser <b>fixa</b> para a ordem
 * alfabética valer. {@link Instant#toString()} omite os milissegundos quando eles são zero:
 * {@code 2026-09-03T01:23:45Z} e {@code 2026-09-03T01:23:45.100Z} têm tamanhos diferentes, e
 * o primeiro ordena <b>depois</b> do segundo em comparação de texto — invertendo a ordem de
 * dois eventos separados por um décimo de segundo. Truncar elimina a variação na origem.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Nulo entra e sai como nulo — data ausente é
 * estado legítimo em várias colunas. Texto ilegível na leitura lança
 * {@link DateTimeParseException}: é dado corrompido no banco, e continuar com uma data
 * inventada seria pior que parar.</p>
 */
public final class InstanteEmTexto {

    /** O tamanho exato do formato. Um valor diferente disto é dado torto. */
    public static final int TAMANHO = 20;

    private InstanteEmTexto() {
    }

    /**
     * O instante como texto ISO-8601 em UTC, terminado em {@code Z}.
     *
     * @param instante pode ser nulo
     * @return {@code null} para entrada nula; nunca texto vazio, que seria indistinguível
     *         de "gravei uma data em branco"
     */
    public static String de(Instant instante) {
        if (instante == null) {
            return null;
        }
        // SEGUNDOS, nao milissegundos: `Instant.toString()` OMITE a fracao quando ela e
        // zero, e a largura variavel quebraria a ordem alfabetica — que e a unica coisa
        // que faz ORDER BY funcionar sem tipo de data.
        return instante.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /**
     * O instante de volta.
     *
     * @param texto pode ser nulo
     * @return {@code null} para entrada nula ou em branco
     * @throws DateTimeParseException texto que não é ISO-8601 — dado corrompido no banco,
     *         e inventar uma data no lugar seria pior que parar
     */
    public static Instant para(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return Instant.parse(texto);
    }

    /**
     * Se o texto está no formato que o banco exige.
     *
     * <p>Existe para a guarda de testes: ela confere que <b>toda</b> coluna de instante do
     * banco está no formato, em vez de confiar que todo caminho de gravação passou por
     * aqui. Confiar seria a convenção que este arquivo existe para não ter.</p>
     */
    public static boolean valido(String texto) {
        if (texto == null || texto.length() != TAMANHO || !texto.endsWith("Z")) {
            return false;
        }
        try {
            Instant.parse(texto);
            return true;
        } catch (DateTimeParseException naoEhInstante) {
            return false;
        }
    }
}
