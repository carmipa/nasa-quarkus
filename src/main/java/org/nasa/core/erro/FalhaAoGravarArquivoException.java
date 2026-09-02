package org.nasa.core.erro;

import java.nio.file.Path;

/**
 * A gravação atômica de um artefato não completou.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Distingue "o disco recusou" de qualquer outra falha do
 * pipeline. Importa porque a reação é específica: artefato não gravado significa medição
 * perdida, e a operação de negócio pode ter acontecido mesmo assim.</p>
 *
 * <p><b>INVARIANTE.</b> O temporário já foi removido antes desta exceção subir — ela
 * nunca deixa lixo para trás.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#ARQUIVO_INACESSIVEL}. O alvo é o nome do
 * arquivo, não o caminho absoluto: caminho absoluto de máquina de usuário vaza PII.</p>
 */
public class FalhaAoGravarArquivoException extends ErroDePipeline {
    public FalhaAoGravarArquivoException(Path destino, Throwable causaTecnica) {
        super("gravar-arquivo-atomico", nomeDe(destino), CausaRaiz.ARQUIVO_INACESSIVEL,
              "falha ao gravar atomicamente", causaTecnica);
    }

    private static String nomeDe(Path p) {
        return p == null || p.getFileName() == null ? null : p.getFileName().toString();
    }
}
