package org.nasa.core.erro;

/**
 * A espera pelo resultado da fila foi interrompida.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "a tarefa falhou" de "pararam de esperar por
 * ela" — dois eventos que o painel precisa contar em colunas diferentes, porque o
 * primeiro é defeito e o segundo é o botão "Parar" funcionando.</p>
 *
 * <p><b>INVARIANTE.</b> Quem constrói esta exceção já restaurou a flag de interrupção da
 * thread. Engolir a interrupção é o erro clássico: a thread segue viva achando que
 * ninguém pediu para ela parar.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#INTERROMPIDO} — que não é defeito, e por
 * isso não conta como erro no veredito da execução.</p>
 */
public class EsperaNaFilaInterrompidaException extends ErroDePipeline {
    public EsperaNaFilaInterrompidaException(Throwable causaTecnica) {
        super("fila-executar-e-aguardar", "fila-execucao-pipeline", CausaRaiz.INTERROMPIDO,
              "espera na fila interrompida", causaTecnica);
    }
}
