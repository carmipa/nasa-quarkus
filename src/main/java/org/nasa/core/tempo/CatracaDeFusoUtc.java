package org.nasa.core.tempo;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import org.nasa.core.erro.FusoHorarioNaoUtcException;
import org.nasa.core.erro.RegistradorDeFalha;
import org.nasa.core.log.Registro;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Recusa o arranque quando a JVM não está em UTC.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O sistema promete que todo instante registrado é UTC.
 * Metade dessa promessa é cumprida por mecanismo no código — {@code Instant} em toda parte,
 * formatação com zona explícita — e essa metade é sólida. A outra metade, o <b>carimbo de
 * hora do log</b>, é escrita pelo framework de logging com o fuso padrão da JVM, que
 * nenhuma linha de código controla. Esta catraca é o que transforma essa metade de
 * esperança em garantia.</p>
 *
 * <p><b>POR QUE UMA CATRACA, E NÃO SÓ A FLAG.</b> A flag {@code -Duser.timezone=UTC} já
 * existia — no JVM de teste, e só nele. Em 02/09/2026 a produção rodou meses de log em
 * {@code -03:00} sem ninguém notar, porque log com fuso errado <b>não parece errado</b>.
 * Flag é combinado; catraca é mecanismo. O que foi esquecido uma vez pode ser esquecido
 * de novo, e o próximo caminho de arranque a existir nasceria com o mesmo furo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Offset zero E fixo.</b> {@code Europe/London} é recusado: rende {@code Z} no
 *       inverno e {@code +01:00} no verão. Invariante que vale metade do ano não é
 *       invariante — e a versão ingênua desta checagem, comparar o offset de <i>agora</i>,
 *       passaria em janeiro e reprovaria em julho.</li>
 *   <li><b>Falha FECHADA.</b> Derruba o arranque em vez de avisar. Aviso em log sobre o
 *       próprio log é a definição de recado que ninguém lê.</li>
 *   <li><b>A mensagem carrega o comando da correção</b>, porque quem topa com isto está a
 *       meio de outra tarefa e não deve ter de investigar nada.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link FusoHorarioNaoUtcException}, registrada
 * antes de subir para que o log diga a causa-raiz e não só o rastro de pilha.</p>
 */
@ApplicationScoped
public class CatracaDeFusoUtc {

    private static final Logger LOG = Logger.getLogger(CatracaDeFusoUtc.class);
    private static final String OPERACAO = "verificar-fuso";

    void aoIniciar(@Observes StartupEvent evento) {
        ZoneId zona = ZoneId.systemDefault();
        try {
            verificar(zona);
        } catch (FusoHorarioNaoUtcException falha) {
            RegistradorDeFalha.registrar(falha);
            throw falha;
        }
        LOG.info(Registro.de(OPERACAO, zona.getId(), "fuso da JVM e UTC"));
    }

    /**
     * A regra, isolada do arranque para o teste poder exercitá-la com qualquer fuso.
     *
     * @param zona o fuso a julgar
     * @throws FusoHorarioNaoUtcException se não for offset zero fixo
     */
    static void verificar(ZoneId zona) {
        var regras = zona.getRules();
        boolean utc = regras.isFixedOffset()
                && regras.getOffset(Instant.EPOCH).getTotalSeconds() == 0;
        if (!utc) {
            throw new FusoHorarioNaoUtcException(zona.getId());
        }
    }
}
