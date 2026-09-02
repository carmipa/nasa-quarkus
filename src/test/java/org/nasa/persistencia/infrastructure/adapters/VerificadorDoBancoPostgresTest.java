package org.nasa.persistencia.infrastructure.adapters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova de que a senha do banco não vaza para o log.
 *
 * <p><b>PROPÓSITO.</b> O verificador escreve no arranque uma linha dizendo contra qual
 * banco o processo está falando. Essa linha vai para o arquivo de log, para a tela e para
 * o print que alguém cola num chat pedindo ajuda. Se a URL levar a senha junto, a
 * credencial de produção passa a existir em todos esses lugares — e nenhum deles tem o
 * cuidado que ela exige.</p>
 *
 * <p><b>Por que isto ganhou teste próprio:</b> é a única garantia do sistema que, quando
 * falha, <b>não produz sintoma nenhum</b>. Um banco fora do ar avisa; uma senha vazando em
 * log não avisa nada — só aparece depois, na mão de quem não devia tê-la. Os dois formatos
 * abaixo são os que realmente carregam senha, e ambos aparecem em documentação de
 * PostgreSQL, então os dois chegam colados de tutorial.</p>
 */
@DisplayName("verificador do banco — a senha NAO vai para o log")
class VerificadorDoBancoPostgresTest {

    @Test
    @DisplayName("CONTROLE POSITIVO: senha na query string some")
    void tiraSenhaDaQueryString() {
        String comSenha = "jdbc:postgresql://vps.exemplo.com:5432/nasa"
                + "?user=nasa_app&password=SenhaSuperSecreta123&ssl=true";   // SEGREDO-FALSO-POSITIVO-AUTORIZADO: senha INVENTADA, fixture do teste que prova que ela e removida do log

        String limpa = VerificadorDoBancoPostgres.semCredencial(comSenha);

        System.out.println("[BANCO] url higienizada: " + limpa);
        assertFalse(limpa.contains("SenhaSuperSecreta123"),
                "a senha sobreviveu na URL que vai para o log: " + limpa);
        assertFalse(limpa.contains("password"), "nem o nome do parametro precisa aparecer");
        assertEquals("jdbc:postgresql://vps.exemplo.com:5432/nasa", limpa,
                "host, porta e base PRECISAM ficar — sem eles a linha de log nao responde "
                        + "'contra qual banco este processo esta falando?'");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: senha no estilo usuario:senha@host tambem some")
    void tiraSenhaDoUserinfo() {
        String comSenha = "jdbc:postgresql://nasa_app:OutraSenha456@vps.exemplo.com:5432/nasa";   // SEGREDO-FALSO-POSITIVO-AUTORIZADO: senha INVENTADA, fixture do teste que prova que ela e removida do log

        String limpa = VerificadorDoBancoPostgres.semCredencial(comSenha);

        System.out.println("[BANCO] url higienizada: " + limpa);
        assertFalse(limpa.contains("OutraSenha456"), "senha vazou: " + limpa);
        assertFalse(limpa.contains("nasa_app"), "o usuario tambem nao precisa ir para o log");
        assertTrue(limpa.contains("vps.exemplo.com:5432/nasa"),
                "o endereco tinha de sobreviver: " + limpa);
    }

    @Test
    @DisplayName("URL sem credencial nenhuma passa intacta")
    void urlLimpaNaoEhMutilada() {
        String limpa = "jdbc:postgresql://localhost:5432/nasa";
        assertEquals(limpa, VerificadorDoBancoPostgres.semCredencial(limpa),
                "higienizar demais esconderia o endereco e tornaria o log inutil");
    }

    @Test
    @DisplayName("URL ausente vira texto declarado, nunca `null` no log")
    void urlAusenteNaoViraNull() {
        // `null` numa linha de log e ambiguo: nao se sabe se o driver nao informou ou se
        // o codigo esqueceu de preencher.
        assertEquals("endereco-nao-informado", VerificadorDoBancoPostgres.semCredencial(null));
        assertEquals("endereco-nao-informado", VerificadorDoBancoPostgres.semCredencial("   "));
    }
}
