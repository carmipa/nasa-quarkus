package org.nasa.core.erro;

/**
 * Alguém pediu para a fila esperar por si mesma.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A fila tem <b>uma</b> thread. Chamar
 * {@code executarEAguardar} de dentro de uma tarefa que já roda nela é aguardar um
 * resultado que só a própria thread poderia produzir: <b>deadlock garantido</b>, e do
 * pior tipo — a aplicação não cai, ela congela, e o sintoma chega como "a tela travou".</p>
 *
 * <p><b>INVARIANTE.</b> Esta exceção existe para transformar um travamento silencioso em
 * erro imediato e legível. É preferível falhar alto no desenvolvimento a pendurar em
 * produção.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#CONFLITO_DE_ESTADO}. A correção é chamar a
 * operação diretamente, sem reenfileirar — já se está dentro da fila.</p>
 */
public class FilaChamadaDeDentroDaFilaException extends ErroDePipeline {
    public FilaChamadaDeDentroDaFilaException() {
        super("fila-executar-e-aguardar", "fila-execucao-pipeline", CausaRaiz.CONFLITO_DE_ESTADO,
              "executarEAguardar chamado de DENTRO da fila: o executor tem uma thread so, "
              + "e esperar aqui e esperar por si mesmo. Chame a operacao diretamente.");
    }
}
