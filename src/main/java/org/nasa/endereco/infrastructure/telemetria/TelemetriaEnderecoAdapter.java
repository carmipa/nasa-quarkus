package org.nasa.endereco.infrastructure.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.io.ArquivoAtomicoUtil;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.endereco.domain.TelemetriaEndereco;
import org.nasa.endereco.domain.ports.TelemetriaEnderecoPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementação da telemetria da fatia {@code endereco}: um JSON, escrito sem corromper.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Guardar, entre execuções e entre reinícios, o que a
 * fatia fez e o que deixou de fazer. É o arquivo que responde <i>"quantos endereços
 * ficaram sem coordenada ontem, e por quê"</i> sem ninguém precisar reler log.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO</b> (§10.3 da planta, todas com cicatriz por trás):</p>
 * <ol>
 *   <li><b>Escrita atômica</b> por {@link ArquivoAtomicoUtil}: se o processo cair no meio,
 *       o arquivo anterior continua íntegro. JSON truncado é lido como "vazio", e "vazio"
 *       e "corrompido" levam a decisões opostas.</li>
 *   <li><b>Arquivo ilegível é PRESERVADO</b> como {@code .corrompido_<instante>}, nunca
 *       apagado — é a única evidência do que aconteceu.</li>
 *   <li><b>Recarrega no {@link PostConstruct}</b>: a telemetria sobrevive a restart.</li>
 *   <li><b>Dedup por chave de negócio</b> (a operação), não append cego: o registro mais
 *       recente substitui o anterior.</li>
 *   <li><b>{@code versaoDoEsquema} no arquivo</b>. Sem ele, um leitor novo lendo arquivo
 *       velho inventa zero — e zero inventado é pior que ausência declarada.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> <b>Nunca lança para quem chama.</b> Falha de
 * gravação vira WARN com causa-raiz e a operação de negócio segue: telemetria é apoio,
 * não função. Arquivo ilegível no boot é movido para quarentena e a memória começa vazia
 * — declarado no log, jamais em silêncio.</p>
 */
@ApplicationScoped
public class TelemetriaEnderecoAdapter implements TelemetriaEnderecoPort {

    private static final Logger LOG = Logger.getLogger(TelemetriaEnderecoAdapter.class);
    private static final String OPERACAO = "telemetria-endereco";

    @Inject
    ObjectMapper json;

    /**
     * O relógio, injetado — nunca {@code Instant.now()} aqui dentro.
     *
     * <p>Todo instante deste sistema é UTC e vem de uma única fonte, para que a virada
     * do dia possa ser testada sem esperar a meia-noite e para que a mesma medição saia
     * igual nesta máquina e no contêiner. Ordem de Paulo, 2026-09-02: UTC no log, na
     * telemetria <b>e no próprio sistema</b> — o legado usava
     * {@code TIMESTAMP WITH LOCAL TIME ZONE}, que grava no fuso do servidor.</p>
     */
    @Inject
    Relogio relogio;

    /**
     * O mapeador que ESTE arquivo usa — configurado aqui, não herdado do ambiente.
     *
     * <p><b>PROPÓSITO.</b> O formato do arquivo de telemetria é um <b>contrato</b>: alguém
     * vai lê-lo daqui a meses, possivelmente com outra ferramenta. Deixá-lo à mercê da
     * configuração global do {@code ObjectMapper} significa que uma mudança feita por
     * outro consumidor — e o global tem muitos — muda o formato deste arquivo em silêncio.
     * No projeto de origem o mapeador global tinha ~20 consumidores em 11 fatias, e mexer
     * nele era transversal.</p>
     *
     * <p><b>A cicatriz que originou este método</b> foi medida em 2026-09-02, lendo o
     * artefato em vez de confiar no verde: {@code registradoEm} saiu como
     * {@code 1788350400.000000000} — timestamp numérico, ilegível e dependente de
     * precisão — porque o mapeador do teste não era o de produção. <b>Instrumento
     * diferente do código medido</b>, que é exatamente o que a regra da medição proíbe.
     * Agora produção e teste chamam este mesmo método, e não há como divergirem.</p>
     *
     * <p><b>FALHA.</b> Não falha: parte de uma cópia, então não altera o mapeador global
     * de ninguém.</p>
     */
    static ObjectMapper mapeadorDeTelemetria(ObjectMapper base) {
        ObjectMapper m = (base == null ? new ObjectMapper() : base.copy());
        m.registerModule(new JavaTimeModule());
        // Instante em ISO-8601 UTC, nunca número: o arquivo é para ser lido por gente.
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }

    private ObjectMapper mapeador() {
        return mapeadorDeTelemetria(json);
    }

    @ConfigProperty(name = "nasa.telemetria.arquivo.endereco", defaultValue = "logs/telemetria_endereco.json")
    String caminho;

    /** Memória viva: chave de negócio → último registro. */
    private final Map<String, TelemetriaEndereco> registros = new ConcurrentHashMap<>();

    /** Contagem causal acumulada da execução corrente. */
    private final Map<CausaRaiz, AtomicInteger> causas = new EnumMap<>(CausaRaiz.class);

    /** O formato no disco. Record de fio — Jackson mora aqui, nunca no domínio. */
    record ArquivoDeTelemetria(int versaoDoEsquema, Map<String, TelemetriaEndereco> registros) {
    }

    @PostConstruct
    void recarregar() {
        Path arquivo = Path.of(caminho);
        if (!Files.isRegularFile(arquivo)) {
            LOG.info(Registro.de(OPERACAO, arquivo.getFileName().toString(),
                    "sem telemetria anterior — comecando vazia"));
            return;
        }
        try {
            ArquivoDeTelemetria lido = mapeador().readValue(Files.readString(arquivo), ArquivoDeTelemetria.class);
            if (lido.versaoDoEsquema() != TelemetriaEndereco.VERSAO_DO_ESQUEMA) {
                // Versão diferente não é corrupção: é formato antigo. Declarar e não ler
                // é melhor que interpretar campos que mudaram de significado.
                LOG.warn(Registro.recusa(OPERACAO, arquivo.getFileName().toString(),
                        "ESQUEMA_INCOMPATIVEL_v" + lido.versaoDoEsquema()));
                return;
            }
            if (lido.registros() != null) {
                registros.putAll(lido.registros());
            }
            LOG.info(Registro.de(OPERACAO, arquivo.getFileName().toString(),
                    "recarregados=" + registros.size()));
        } catch (Exception ilegivel) {
            // Preserva a evidência e segue. Perder a telemetria antiga é ruim; apagar a
            // prova do que a corrompeu é pior.
            try {
                Path quarentena = ArquivoAtomicoUtil.preservarCorrompido(arquivo, relogio.agora());
                LOG.warn(Registro.recusa(OPERACAO, quarentena.getFileName().toString(),
                        CausaRaiz.ARQUIVO_CORROMPIDO.name()), ilegivel);
            } catch (ErroDePipeline naoConseguiu) {
                LOG.error(naoConseguiu.linhaDeLog(), naoConseguiu);
            }
        }
    }

    @Override
    public void registrar(TelemetriaEndereco evento) {
        if (evento == null || evento.operacao() == null) {
            LOG.warn(Registro.recusa(OPERACAO, "evento-sem-chave", CausaRaiz.DADO_AUSENTE.name()));
            return;
        }
        registros.put(evento.operacao(), evento);   // dedup por chave de negócio
        gravar();
    }

    @Override
    public Optional<TelemetriaEndereco> ultimo(String operacao) {
        return Optional.ofNullable(registros.get(operacao));
    }

    @Override
    public void contar(CausaRaiz causa) {
        CausaRaiz alvo = causa == null ? CausaRaiz.NAO_CLASSIFICADA : causa;
        causas.computeIfAbsent(alvo, c -> new AtomicInteger()).incrementAndGet();
    }

    /** Snapshot da contagem causal desta execução — usado para montar o KPI causal. */
    public Map<CausaRaiz, Integer> causasAcumuladas() {
        Map<CausaRaiz, Integer> copia = new EnumMap<>(CausaRaiz.class);
        causas.forEach((c, n) -> copia.put(c, n.get()));
        return copia;
    }

    private void gravar() {
        try {
            Map<String, TelemetriaEndereco> ordenado = new LinkedHashMap<>(registros);
            String conteudo = mapeador().writerWithDefaultPrettyPrinter().writeValueAsString(
                    new ArquivoDeTelemetria(TelemetriaEndereco.VERSAO_DO_ESQUEMA, ordenado));
            ArquivoAtomicoUtil.gravar(Path.of(caminho), conteudo);
        } catch (ErroDePipeline falhaDeDisco) {
            LOG.warn(falhaDeDisco.linhaDeLog(), falhaDeDisco);
        } catch (Exception falhaDeSerializacao) {
            LOG.warn(Registro.recusa(OPERACAO, caminho, CausaRaiz.DADO_INVALIDO.name()),
                    falhaDeSerializacao);
        }
    }
}
