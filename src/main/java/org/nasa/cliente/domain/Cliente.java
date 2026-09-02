package org.nasa.cliente.domain;

import org.nasa.cliente.domain.exceptions.ClienteInvalidoException;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Uma pessoa cadastrada para receber alerta de desastre.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o destinatário do sistema. Tudo o mais — endereço,
 * contato, proximidade de evento — existe para decidir se e como avisar esta pessoa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nome e sobrenome não são vazios.</b> Cadastro sem nome não tem como ser
 *       conferido por quem opera — e a confirmação de exclusão precisa <b>nomear</b> quem
 *       vai ser apagado, que é a defesa contra apagar o registro errado por semelhança.</li>
 *   <li><b>Data de nascimento é {@link LocalDate}</b>, não texto. No legado era
 *       {@code VARCHAR2(10)} sem forma: não ordenava, não comparava, não validava. E é
 *       data <b>civil</b>, sem hora e sem fuso — aniversário não muda quando a pessoa
 *       viaja. É a única data do sistema que legitimamente não é {@code Instant}.</li>
 *   <li><b>{@code id} nulo significa "ainda não gravado"</b>, nunca zero: zero seria um
 *       identificador válido que não existe.</li>
 *   <li><b>Domínio puro</b>: sem framework, sem anotação de serialização, sem I/O.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O construtor recusa campo vazio ou ausente
 * com {@link ClienteInvalidoException}, nomeando o campo. Cliente inválido nunca chega a
 * existir como objeto, então nenhum repositório o grava por engano.</p>
 *
 * @param id             nulo enquanto não gravado
 * @param nome           primeiro nome
 * @param sobrenome      restante do nome
 * @param dataNascimento data civil, sem hora
 * @param documento      identidade normalizada
 * @param criadoEm       instante UTC do cadastro; nulo enquanto não gravado
 */
public record Cliente(Long id, String nome, String sobrenome,
                      LocalDate dataNascimento, Documento documento, Instant criadoEm) {

    public Cliente {
        nome = exigir(nome, "nome");
        sobrenome = exigir(sobrenome, "sobrenome");
        if (dataNascimento == null) {
            throw new ClienteInvalidoException("dataNascimento", "ausente");
        }
        if (documento == null) {
            throw new ClienteInvalidoException("documento", "ausente");
        }
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ClienteInvalidoException(campo, "vazio");
        }
        return valor.trim();
    }

    /** Novo cadastro: id e instante vêm do repositório, no momento da gravação. */
    public static Cliente novo(String nome, String sobrenome, LocalDate nascimento, Documento doc) {
        return new Cliente(null, nome, sobrenome, nascimento, doc, null);
    }

    /** O mesmo cliente, com os dados de cadastro alterados. */
    public Cliente com(String nome, String sobrenome, LocalDate nascimento, Documento doc) {
        return new Cliente(this.id, nome, sobrenome, nascimento, doc, this.criadoEm);
    }

    /** Como a tela mostra, e como a confirmação de exclusão nomeia o alvo. */
    public String nomeCompleto() {
        return nome + " " + sobrenome;
    }
}
