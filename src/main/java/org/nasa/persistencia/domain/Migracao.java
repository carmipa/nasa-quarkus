package org.nasa.persistencia.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Uma migração de esquema: número, nome, SQL e a soma que a torna imutável.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O esquema do banco muda ao longo do projeto, e cada
 * mudança precisa acontecer <b>uma vez só</b>, <b>na mesma ordem</b>, em toda máquina que
 * rodar o sistema. Esta é a unidade dessa evolução.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O checksum é do CONTEÚDO.</b> É ele que denuncia a edição de uma migração já
 *       aplicada — o defeito silencioso mais caro desta camada, porque o banco de quem
 *       rodou a versão antiga fica diferente do de quem rodou a nova, com o mesmo número
 *       de versão nos dois.</li>
 *   <li><b>Versão positiva e nome não vazio.</b> Migração sem identidade não tem como ser
 *       registrada nem auditada.</li>
 *   <li><b>Record puro</b>: sem framework, sem I/O. Quem lê arquivo é o adaptador.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Construtor recusa versão ≤ 0, nome em branco
 * ou SQL vazio, com {@link org.nasa.persistencia.domain.exceptions.MigracaoInvalidaException}.
 * Migração vazia é quase sempre arquivo que não foi lido — e aplicar "nada" com sucesso
 * registraria a versão sem ter feito o trabalho.</p>
 *
 * @param versao   número sequencial, único
 * @param nome     descrição curta, vinda do nome do arquivo
 * @param sql      o conteúdo a executar
 */
public record Migracao(int versao, String nome, String sql) {

    public Migracao {
        if (versao <= 0) {
            throw new org.nasa.persistencia.domain.exceptions.MigracaoInvalidaException(
                    String.valueOf(versao), "versao tem de ser positiva");
        }
        if (nome == null || nome.isBlank()) {
            throw new org.nasa.persistencia.domain.exceptions.MigracaoInvalidaException(
                    String.valueOf(versao), "nome vazio");
        }
        if (sql == null || sql.isBlank()) {
            throw new org.nasa.persistencia.domain.exceptions.MigracaoInvalidaException(
                    String.valueOf(versao), "SQL vazio — quase sempre e arquivo que nao foi lido");
        }
    }

    /**
     * SHA-256 do SQL, em hexadecimal minúsculo.
     *
     * <p><b>FALHA:</b> algoritmo ausente na JVM é impossível na prática (SHA-256 é
     * obrigatório na plataforma), mas se acontecer vira erro alto — silenciar aqui
     * desligaria a única proteção contra migração editada.</p>
     */
    public String checksum() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(sql.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossivel) {
            throw new org.nasa.persistencia.domain.exceptions.MigracaoInvalidaException(
                    String.valueOf(versao), "SHA-256 indisponivel nesta JVM");
        }
    }

    /** Identificação legível para log e mensagem de erro. */
    public String identificacao() {
        return "V" + versao + "__" + nome;
    }
}
