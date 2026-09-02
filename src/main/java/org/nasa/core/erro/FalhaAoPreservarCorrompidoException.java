package org.nasa.core.erro;

import java.nio.file.Path;

/**
 * Não foi possível mover para quarentena um arquivo ilegível.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a falha mais grave da família de arquivo: o sistema
 * encontrou conteúdo corrompido e <b>não conseguiu nem preservá-lo</b>. Neste ponto
 * ninguém sabe mais em que estado o disco está, e seguir escrevendo por cima seria
 * destruir a única evidência do que aconteceu.</p>
 *
 * <p><b>INVARIANTE.</b> O arquivo original NUNCA é apagado — nem aqui, nem no caminho
 * feliz. Quarentena é renomeação, não exclusão.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#ARQUIVO_CORROMPIDO}. Falha fechada: quem
 * chamou deve parar, não tentar de novo.</p>
 */
public class FalhaAoPreservarCorrompidoException extends ErroDePipeline {
    public FalhaAoPreservarCorrompidoException(Path arquivo, Throwable causaTecnica) {
        super("preservar-arquivo-corrompido",
              arquivo == null || arquivo.getFileName() == null ? null : arquivo.getFileName().toString(),
              CausaRaiz.ARQUIVO_CORROMPIDO, "falha ao mover arquivo corrompido para quarentena",
              causaTecnica);
    }
}
