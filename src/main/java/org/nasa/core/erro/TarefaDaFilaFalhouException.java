package org.nasa.core.erro;

/**
 * A tarefa executada na fila lançou, e quem esperava o resultado precisa saber.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Em {@code submeter} a fila sobrevive à tarefa e segue
 * para a próxima; em {@code executarEAguardar} existe alguém do outro lado esperando uma
 * resposta. Devolver silêncio ali seria a borda mentindo — o cliente veria sucesso vazio.</p>
 *
 * <p><b>INVARIANTE.</b> A causa técnica original é preservada como {@code cause}: perder
 * o motivo é o defeito que a planta mais cobra.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#NAO_CLASSIFICADA} quando a tarefa não é
 * uma {@link ErroDePipeline}; quando é, a causa dela é <b>herdada</b>, para o KPI causal
 * apontar o motivo real e não o invólucro.</p>
 */
public class TarefaDaFilaFalhouException extends ErroDePipeline {
    public TarefaDaFilaFalhouException(Throwable causaTecnica) {
        super("fila-executar-tarefa", "fila-execucao-pipeline", causaDe(causaTecnica),
              "tarefa da fila falhou", causaTecnica);
    }

    private static CausaRaiz causaDe(Throwable t) {
        return (t instanceof ErroDePipeline e) ? e.causaRaiz() : CausaRaiz.NAO_CLASSIFICADA;
    }
}
