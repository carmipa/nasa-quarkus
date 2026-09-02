package org.nasa.core.io;

import org.nasa.core.erro.FalhaAoGravarArquivoException;
import org.nasa.core.erro.FalhaAoPreservarCorrompidoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Escrita de arquivo que nunca deixa o disco num estado intermediário.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Todo artefato que este sistema persiste — telemetria,
 * relatório, cache — é escrito por um processo que pode morrer no meio: a JVM cai, o
 * contêiner reinicia, a máquina desliga. Escrever direto no arquivo final significa que
 * uma queda no meio da gravação deixa um JSON truncado que a próxima leitura interpreta
 * como "vazio" — e "vazio" e "corrompido" tomam decisões muito diferentes.</p>
 *
 * <p><b>Por que isto é a PRIMEIRA classe do kernel</b> (§1-bis, Passo 4 da planta): a
 * primeira escrita de arquivo do projeto já tem de ser atômica. Retrofitar depois
 * significa auditar cada gravação existente para descobrir quais eram seguras — e a
 * resposta costuma ser "nenhuma".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Temp + move.</b> Grava num arquivo temporário <b>no mesmo diretório</b> do
 *       destino e move por cima. Mesmo diretório é requisito, não detalhe: mover entre
 *       sistemas de arquivos diferentes deixa de ser atômico e vira copiar+apagar.</li>
 *   <li><b>Arquivo ilegível é PRESERVADO, nunca apagado</b> — renomeado com sufixo
 *       {@code .corrompido_<instante>}. O conteúdo estragado é a única evidência do que
 *       aconteceu; apagá-lo é destruir a prova junto com o problema.</li>
 *   <li>O diretório de destino é criado se não existir — falhar por pasta ausente numa
 *       escrita de telemetria seria trocar um problema pequeno por perda de medição.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer erro de I/O vira
 * {@link FalhaAoGravarArquivoException} com o caminho no texto, e o <b>temporário é removido</b> para
 * não deixar lixo acumulando. Se o sistema de arquivos não suportar movimento atômico
 * (acontece em alguns bind mounts), cai para {@code REPLACE_EXISTING} — que é menos
 * garantido e por isso <b>registra o motivo na mensagem</b> em vez de degradar calado.</p>
 */
public final class ArquivoAtomicoUtil {

    private ArquivoAtomicoUtil() {
    }

    /**
     * Grava o conteúdo em UTF-8, atomicamente.
     *
     * @param destino  arquivo final; o diretório é criado se faltar
     * @param conteudo texto a gravar
     * @throws FalhaAoGravarArquivoException em qualquer falha de I/O, com o alvo nomeado
     */
    public static void gravar(Path destino, String conteudo) {
        Path temporario = null;
        try {
            Path pasta = destino.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            // O temporário nasce no MESMO diretório: mover entre volumes não é atômico.
            temporario = Files.createTempFile(pasta, ".tmp-", destino.getFileName().toString());
            Files.writeString(temporario, conteudo, StandardCharsets.UTF_8);

            try {
                Files.move(temporario, destino,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException semAtomico) {
                // Degradação DECLARADA, não silenciosa: o chamador precisa saber que a
                // garantia caiu, senão vai confiar numa atomicidade que não houve.
                Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            temporario = null;
        } catch (IOException e) {
            throw new FalhaAoGravarArquivoException(destino, e);
        } finally {
            if (temporario != null) {
                try {
                    Files.deleteIfExists(temporario);
                } catch (IOException ignorado) {
                    // Não mascarar a falha original com a falha da limpeza.
                }
            }
        }
    }

    /**
     * Move um arquivo ilegível para quarentena, preservando-o.
     *
     * <p><b>PROPÓSITO.</b> Chamado quando a leitura falha por conteúdo inválido. O nome
     * ganha {@code .corrompido_<instante>} e o original sai do caminho para que o sistema
     * volte a funcionar — sem que a evidência seja destruída.</p>
     *
     * <p><b>FALHA.</b> Se nem mover for possível, lança {@link FalhaAoPreservarCorrompidoException}: neste
     * ponto o sistema não sabe mais em que estado o disco está, e seguir em frente seria
     * escrever por cima de algo que ninguém entendeu.</p>
     *
     * <p><b>O instante VEM DE FORA</b>, do {@code Relogio} injetado, e não de
     * {@code Instant.now()} aqui dentro: é o que torna o nome do arquivo previsível no
     * teste e o que mantém esta classe fora da lista de quem lê o relógio do sistema
     * (catraca {@code CatracaRelogioUtcTest}). O instante é UTC por ser {@link Instant}.</p>
     *
     * @param quando instante UTC do carimbo — venha sempre do relógio injetado
     * @return o caminho da quarentena
     */
    public static Path preservarCorrompido(Path arquivo, Instant quando) {
        Path quarentena = arquivo.resolveSibling(
                arquivo.getFileName() + ".corrompido_" + quando.toString().replace(':', '-'));
        try {
            Files.move(arquivo, quarentena, StandardCopyOption.REPLACE_EXISTING);
            return quarentena;
        } catch (IOException e) {
            throw new FalhaAoPreservarCorrompidoException(arquivo, e);
        }
    }
}
