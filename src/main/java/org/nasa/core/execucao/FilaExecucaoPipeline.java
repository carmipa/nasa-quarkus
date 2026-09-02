package org.nasa.core.execucao;

import jakarta.annotation.PreDestroy;
import org.jboss.logging.Logger;
import jakarta.enterprise.context.ApplicationScoped;
import org.nasa.core.erro.EsperaNaFilaInterrompidaException;
import org.nasa.core.erro.FilaChamadaDeDentroDaFilaException;
import org.nasa.core.erro.TarefaDaFilaFalhouException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.log.Registro;

/**
 * A fila única — todo trabalho pesado passa por aqui, um de cada vez.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Sincronizar com a NASA, geocodificar um lote de
 * endereços e recalcular estatísticas são operações longas que disputam os mesmos
 * recursos escassos: <b>um</b> arquivo SQLite com um escritor por vez, e provedores
 * externos com limite de vazão declarado (o Nominatim público aceita 1 requisição por
 * segundo). Duas dessas operações em paralelo não terminam na metade do tempo — terminam
 * no mesmo tempo somado, com o dobro de chance de estourar limite e de colidir na
 * escrita.</p>
 *
 * <p><b>Isto é CORRETUDE, não desempenho.</b> É a distinção que a planta faz em §7.1, e
 * ela importa: quem lê "fila" como otimização eventualmente a remove para "ganhar
 * paralelismo", e o que aparece depois é escrita concorrente falhando de forma
 * intermitente — o tipo de bug que não se reproduz na máquina de quem investiga.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Uma thread só</b>, nomeada e daemon. O nome não é enfeite: sem ele, um dump de
 *       thread mostra {@code pool-3-thread-1} e ninguém sabe o que estava rodando.</li>
 *   <li><b>Nunca chamar {@link #executarEAguardar} de dentro de uma tarefa que já roda na
 *       fila</b> — o executor tem uma thread só, e a espera seria por si mesmo:
 *       <b>deadlock garantido</b>. Isto não é aviso em comentário; é verificado em
 *       runtime e recusado alto.</li>
 *   <li><b>Parada é cooperativa.</b> {@link #parar()} interrompe e descarta o que estava
 *       enfileirado; os laços longos conferem a interrupção e encerram no próximo ponto
 *       seguro, <b>preservando o progresso já salvo</b>. Nunca {@code Thread.stop}.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Exceção dentro de uma tarefa submetida não
 * derruba a fila: ela é registrada e a fila segue para a próxima. Em
 * {@link #executarEAguardar}, a exceção é <b>reembrulhada e propagada ao chamador</b> —
 * quem espera resposta precisa saber que ela não veio. Interrupção durante a espera
 * restaura a flag e vira {@link EsperaNaFilaInterrompidaException}, nunca é engolida.</p>
 */
@ApplicationScoped
public class FilaExecucaoPipeline {

    private static final Logger LOG = Logger.getLogger(FilaExecucaoPipeline.class);

    /** Nome da thread. Também é como {@link #naFila()} se reconhece. */
    private static final String NOME_DA_THREAD = "pipeline-fila-execucao";

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, NOME_DA_THREAD);
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean ocupada = new AtomicBoolean(false);

    /** Estamos rodando DENTRO da fila neste instante? */
    private static boolean naFila() {
        return NOME_DA_THREAD.equals(Thread.currentThread().getName());
    }

    /**
     * Enfileira trabalho longo e devolve na hora.
     *
     * <p><b>INVARIANTE do chamador:</b> a borda que enfileira <b>valida o que dá para
     * validar ANTES</b> — caminho existe, formato confere, permissão bate — e devolve
     * 4xx. Cicatriz medida no projeto de origem: 7 rotas devolviam HTTP 200 "iniciada"
     * para pasta inexistente; o trabalho ia para a fila, falhava lá dentro, e a tela
     * dizia que tinha começado. <b>A borda assíncrona mente por construção</b> se
     * ninguém a obrigar a conferir antes.</p>
     */
    public void submeter(Runnable tarefa) {
        executor.submit(() -> {
            ocupada.set(true);
            try {
                tarefa.run();
            } catch (RuntimeException e) {
                // A fila sobrevive à tarefa — mas o motivo NUNCA se perde. Falha que veio
                // nomeada traz a própria causa-raiz; a que não veio cai em
                // NAO_CLASSIFICADA, que é greppável e conta como defeito de classificação.
                if (e instanceof ErroDePipeline erro) {
                    LOG.error(erro.linhaDeLog(), e);
                } else {
                    LOG.error(Registro.recusa("fila-executar-tarefa", "fila-execucao-pipeline",
                            CausaRaiz.NAO_CLASSIFICADA.name()), e);
                }
            } finally {
                ocupada.set(false);
            }
        });
    }

    /**
     * Executa na fila e <b>espera</b> o resultado — para endpoint que responde no próprio
     * request.
     *
     * @throws FilaChamadaDeDentroDaFilaException se chamado de dentro da própria fila
     * @throws TarefaDaFilaFalhouException        se a tarefa falhar
     * @throws EsperaNaFilaInterrompidaException  se a espera for interrompida
     */
    public <T> T executarEAguardar(Callable<T> tarefa) {
        if (naFila()) {
            throw new FilaChamadaDeDentroDaFilaException();
        }
        Future<T> futuro = executor.submit(() -> {
            ocupada.set(true);
            try {
                return tarefa.call();
            } finally {
                ocupada.set(false);
            }
        });
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // nunca engolir a interrupção
            throw new EsperaNaFilaInterrompidaException(e);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            throw new TarefaDaFilaFalhouException(causa);
        }
    }

    /**
     * A fila está trabalhando agora?
     *
     * <p><b>PROPÓSITO.</b> Conferir <b>antes</b> de bloquear. Sem isto, um GET rápido fica
     * pendurado atrás de uma sincronização longa e a tela parece travada.</p>
     */
    public boolean ocupada() {
        return ocupada.get();
    }

    /**
     * Parada cooperativa: interrompe a tarefa atual e descarta as enfileiradas.
     *
     * <p><b>FALHA.</b> Não força nada. Se a tarefa ignorar a interrupção, ela termina
     * sozinha — {@code Thread.stop} deixaria estado pela metade, que é pior que esperar.</p>
     */
    public void parar() {
        executor.shutdownNow();
    }

    @PreDestroy
    void encerrar() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
