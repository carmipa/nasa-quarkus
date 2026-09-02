package org.nasa.contato.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.contato.domain.exceptions.ContatoInvalidoException;
import org.nasa.contato.domain.exceptions.EmailInvalidoException;
import org.nasa.contato.domain.exceptions.TipoDeContatoDesconhecidoException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do domínio de contato — sem banco, sem rede, sem contêiner.
 *
 * <p><b>PROPÓSITO.</b> O defeito característico desta fatia não produz erro: produz
 * <b>silêncio</b> na hora do desastre. Estes testes atacam as três formas de chegar lá —
 * e-mail que não é e-mail, telefone gravado em quatro formas diferentes, e tipo de
 * contato que ninguém consegue procurar depois.</p>
 */
@DisplayName("dominio de contato — e-mail, telefone e o tipo que decide quem e avisado")
class ContatoDominioTest {

    // -------------------------------------------------------------------- e-mail

    @Test
    @DisplayName("e-mail e NORMALIZADO: caixa e espaco nao criam duas caixas postais")
    void emailNormalizado() {
        // Sem isto, "Ana@Exemplo.com " e "ana@exemplo.com" viram DOIS contatos, o UNIQUE
        // do banco nao enxerga a duplicata, e o alerta sai duas vezes para a mesma pessoa.
        assertEquals("ana@exemplo.com", new Email("  Ana@Exemplo.COM  ").valor());
        assertEquals("exemplo.com", new Email("Ana@Exemplo.com").dominio());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: os e-mails que nao chegam a lugar nenhum sao recusados")
    void emailInvalidoEhRecusado() {
        // Sem estes casos, um construtor vazio passaria em todos os testes acima.
        for (String ruim : new String[] { null, "", "   ", "semarroba.com", "a@b@c.com",
                "@exemplo.com", "ana@", "ana@semponto", "ana@.com", "ana@exemplo.",
                "ana silva@exemplo.com" }) {
            assertThrows(EmailInvalidoException.class, () -> new Email(ruim),
                    "deveria ter recusado: " + ruim);
        }
    }

    @Test
    @DisplayName("a mensagem de erro NAO carrega o e-mail — e dado pessoal")
    void mensagemDeErroNaoVazaOEmail() {
        // Mensagem de erro vai para arquivo de log, para tela e para print colado em chat.
        var erro = assertThrows(EmailInvalidoException.class,
                () -> new Email("paulo.secreto@empresadocliente"));
        System.out.println("[CONTATO] " + erro.linhaDeLog());
        assertFalse(erro.getMessage().contains("paulo.secreto"),
                "o e-mail vazou na mensagem: " + erro.getMessage());
    }

    // ------------------------------------------------------------------ telefone

    @Test
    @DisplayName("telefone guarda so DIGITOS — a mesma cicatriz do documento do cliente")
    void telefoneSoDigitos() {
        var c = Contato.novo("11", "3456-7890", "98765-4321", null,
                new Email("ana@exemplo.com"), TipoContato.PRINCIPAL);

        assertEquals("34567890", c.telefone(), "guardar como digitado faz o mesmo numero "
                + "existir em quatro formas, e nenhuma busca acha as outras tres");
        assertEquals("987654321", c.celular());
        assertEquals("(11) 98765-4321", c.contatoTelefonicoFormatado(),
                "a tela mostra a forma que a pessoa reconhece");
    }

    @Test
    @DisplayName("campo opcional em branco vira AUSENTE, nunca string vazia")
    void opcionalEmBrancoViraAusente() {
        // "Nao tem WhatsApp" e "tem WhatsApp em branco" apareceriam iguais na tela — o
        // segundo como um numero por preencher que ninguem vai preencher.
        var c = Contato.novo("", "  ", null, "(  )  -  ",
                new Email("ana@exemplo.com"), TipoContato.PRINCIPAL);
        assertNull(c.ddd());
        assertNull(c.telefone());
        assertNull(c.whatsapp(), "texto sem digito nenhum e ausencia com cara de preenchido");
        assertTrue(c.dddOpcional().isEmpty());
        assertEquals("—", c.contatoTelefonicoFormatado());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: telefone e DDD de tamanho errado sao recusados")
    void tamanhoErradoEhRecusado() {
        var email = new Email("ana@exemplo.com");
        // DDD com tres digitos e quase sempre o numero colado no campo errado.
        assertThrows(ContatoInvalidoException.class,
                () -> Contato.novo("119", null, null, null, email, TipoContato.PRINCIPAL));
        // Sete digitos e campo pela metade.
        assertThrows(ContatoInvalidoException.class,
                () -> Contato.novo("11", "3456789", null, null, email, TipoContato.PRINCIPAL));
        // Onze digitos e DDD grudado no numero.
        assertThrows(ContatoInvalidoException.class,
                () -> Contato.novo("11", null, "11987654321", null, email, TipoContato.PRINCIPAL));
    }

    @Test
    @DisplayName("e-mail e OBRIGATORIO: contato so com telefone nao avisa ninguem")
    void emailEhObrigatorio() {
        assertThrows(ContatoInvalidoException.class,
                () -> Contato.novo("11", "34567890", null, null, null, TipoContato.PRINCIPAL));
    }

    // ---------------------------------------------------------------------- tipo

    @Test
    @DisplayName("o tipo aceita a caixa que vier, mas NAO aceita valor inventado")
    void tipoAceitaCaixaMasNaoInvencao() {
        assertEquals(TipoContato.EMERGENCIA, TipoContato.de("emergencia"));
        assertEquals(TipoContato.EMERGENCIA, TipoContato.de("  EMERGENCIA  "));
        assertEquals(TipoContato.PRINCIPAL, TipoContato.de(null), "sem escolha, o mais conservador");
        assertEquals(TipoContato.PRINCIPAL, TipoContato.de(""));

        // O legado aceitava texto livre, e era assim que "Principal" e "principal"
        // viravam duas classificacoes para a mesma intencao.
        var erro = assertThrows(TipoDeContatoDesconhecidoException.class,
                () -> TipoContato.de("Pincipal"));
        assertTrue(erro.getMessage().contains("EMERGENCIA"),
                "a mensagem tem de listar os aceitos, senao a pessoa adivinha: "
                        + erro.getMessage());
    }

    @Test
    @DisplayName("SO EMERGENCIA recebe alerta — e o padrao NAO e emergencia")
    void soEmergenciaRecebeAlerta() {
        // Promover alguem a contato de emergencia por engano faz uma pessoa receber aviso
        // de desastre que nao pediu — e faz parecer que a cobertura existe.
        assertTrue(TipoContato.EMERGENCIA.recebeAlerta());
        assertFalse(TipoContato.PRINCIPAL.recebeAlerta());
        assertFalse(TipoContato.ALTERNATIVO.recebeAlerta());
        assertFalse(TipoContato.COMERCIAL.recebeAlerta());
        assertFalse(TipoContato.de(null).recebeAlerta(),
                "quem nao escolhe NAO pode entrar na lista de avisos de desastre");
    }
}
