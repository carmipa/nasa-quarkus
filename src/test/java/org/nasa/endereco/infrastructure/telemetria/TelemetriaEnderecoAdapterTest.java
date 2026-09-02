package org.nasa.endereco.infrastructure.telemetria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.telemetria.Veredito;
import org.nasa.endereco.domain.TelemetriaEndereco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do adaptador de telemetria — o que ele grava, o que ele recarrega, e o que ele
 * <b>recusa a destruir</b>.
 *
 * <p><b>PROPÓSITO.</b> Telemetria que se corrompe silenciosamente é pior que telemetria
 * ausente: ela devolve números errados com a mesma cara dos certos. Os testes que
 * importam aqui são os de sobrevivência — reinício e arquivo estragado.</p>
 */
@DisplayName("TelemetriaEnderecoAdapter — grava sem corromper, recarrega e preserva a evidencia")
class TelemetriaEnderecoAdapterTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");

    private static TelemetriaEnderecoAdapter adaptadorEm(Path arquivo) {
        TelemetriaEnderecoAdapter a = new TelemetriaEnderecoAdapter();
        a.caminho = arquivo.toString();
        // MESMO caminho de producao: o mapeador vem do proprio adaptador.
        // Construir um aqui foi o defeito medido em 02/09 — instrumento
        // diferente do codigo medido, e o artefato saiu com data numerica.
        a.json = TelemetriaEnderecoAdapter.mapeadorDeTelemetria(new ObjectMapper());
        a.relogio = () -> AGORA;   // relogio injetado: UTC e parado, testavel
        return a;
    }

    private static TelemetriaEndereco evento(String operacao, int resolvidos, int semCoord) {
        return TelemetriaEndereco.avaliar(operacao, AGORA, resolvidos, semCoord,
                Map.of(CausaRaiz.DADO_AUSENTE, semCoord), true);
    }

    @Test
    @DisplayName("grava o registro e o arquivo carrega a versao do esquema")
    void gravaComVersaoDoEsquema(@TempDir Path pasta) throws IOException {
        Path arquivo = pasta.resolve("telemetria_endereco.json");
        var a = adaptadorEm(arquivo);
        a.recarregar();                       // primeiro boot: sem arquivo anterior

        a.registrar(evento("resolver-lote", 38, 4));

        assertTrue(Files.exists(arquivo), "a telemetria nao chegou ao disco");
        String conteudo = Files.readString(arquivo);
        System.out.println("[TELEMETRIA] " + conteudo.replace('\n', ' ').substring(0, Math.min(220, conteudo.length())));

        assertTrue(conteudo.contains("\"registradoEm\" : \"2026-09-02T12:00:00Z\""),
                "o instante tem de sair em ISO-8601 UTC, nunca como numero: arquivo de "
                        + "telemetria e para ser lido por gente, e timestamp numerico depende "
                        + "de precisao e de configuracao global");
        assertTrue(conteudo.contains("\"versaoDoEsquema\" : 1"),
                "sem versao no arquivo, um leitor novo lendo arquivo velho inventa zero");
        assertTrue(conteudo.contains("\"resolvidos\" : 38"), "faltou o contador do que AGIU");
        assertTrue(conteudo.contains("\"semCoordenada\" : 4"), "faltou o contador do que se ABSTEVE");
        assertTrue(conteudo.contains("ATENCAO"), "faltou o veredito");
        assertTrue(conteudo.contains("ENDERECOS_SEM_COORDENADA=4"), "faltou o motivo do veredito");
    }

    @Test
    @DisplayName("a telemetria SOBREVIVE ao reinicio — recarrega do disco")
    void sobreviveAoReinicio(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("telemetria_endereco.json");
        var antes = adaptadorEm(arquivo);
        antes.recarregar();
        antes.registrar(evento("resolver-lote", 38, 4));

        // Outra instância, como acontece depois de um restart do contêiner.
        var depois = adaptadorEm(arquivo);
        depois.recarregar();

        var lido = depois.ultimo("resolver-lote");
        assertTrue(lido.isPresent(), "a telemetria nao sobreviveu ao reinicio");
        assertEquals(38, lido.get().resolvidos());
        assertEquals(4, lido.get().semCoordenada());
        assertEquals(Veredito.ATENCAO, lido.get().veredito());
    }

    @Test
    @DisplayName("dedup por chave de negocio: o registro mais recente substitui, nao empilha")
    void dedupPorChaveDeNegocio(@TempDir Path pasta) {
        Path arquivo = pasta.resolve("telemetria_endereco.json");
        var a = adaptadorEm(arquivo);
        a.recarregar();

        a.registrar(evento("resolver-lote", 10, 0));
        a.registrar(evento("resolver-lote", 99, 1));
        a.registrar(evento("outra-operacao", 5, 0));

        assertEquals(99, a.ultimo("resolver-lote").orElseThrow().resolvidos(),
                "a chave de negocio deduplica: o mais recente manda");
        assertEquals(5, a.ultimo("outra-operacao").orElseThrow().resolvidos(),
                "operacoes diferentes nao se sobrescrevem");
    }

    @Test
    @DisplayName("ausencia e AUSENCIA: operacao nunca registrada devolve vazio, nao zero")
    void ausenciaNaoEhZero(@TempDir Path pasta) {
        var a = adaptadorEm(pasta.resolve("telemetria_endereco.json"));
        a.recarregar();
        assertTrue(a.ultimo("nunca-rodou").isEmpty(),
                "devolver zero aqui faria 'nunca rodou' parecer 'rodou e nao fez nada'");
    }

    @Test
    @DisplayName("CONTROLE: arquivo corrompido e PRESERVADO em quarentena, nunca apagado")
    void arquivoCorrompidoEhPreservado(@TempDir Path pasta) throws IOException {
        Path arquivo = pasta.resolve("telemetria_endereco.json");
        Files.writeString(arquivo, "{ isto nao e json valido ");

        var a = adaptadorEm(arquivo);
        a.recarregar();   // não lança: telemetria é apoio, não função

        assertFalse(Files.exists(arquivo), "o corrompido saiu do caminho para o sistema seguir");
        try (var s = Files.list(pasta)) {
            var quarentena = s.filter(p -> p.getFileName().toString().contains(".corrompido_")).toList();
            assertEquals(1, quarentena.size(), "a evidencia tem de sobreviver: 1 arquivo em quarentena");
            System.out.println("[TELEMETRIA] quarentena: " + quarentena.get(0).getFileName());
            assertTrue(Files.readString(quarentena.get(0)).contains("isto nao e json valido"),
                    "o conteudo estragado e a unica evidencia do que aconteceu");
        }

        // E o sistema volta a funcionar, começando vazio — declarado, não silencioso.
        a.registrar(evento("resolver-lote", 1, 0));
        assertTrue(a.ultimo("resolver-lote").isPresent());
    }

    @Test
    @DisplayName("contagem causal acumula por causa-raiz — e nulo vira NAO_CLASSIFICADA")
    void contagemCausal(@TempDir Path pasta) {
        var a = adaptadorEm(pasta.resolve("telemetria_endereco.json"));
        a.contar(CausaRaiz.PROVEDOR_INDISPONIVEL);
        a.contar(CausaRaiz.PROVEDOR_INDISPONIVEL);
        a.contar(CausaRaiz.DADO_AUSENTE);
        a.contar(null);

        var causas = a.causasAcumuladas();
        assertEquals(2, causas.get(CausaRaiz.PROVEDOR_INDISPONIVEL));
        assertEquals(1, causas.get(CausaRaiz.DADO_AUSENTE));
        assertEquals(1, causas.get(CausaRaiz.NAO_CLASSIFICADA),
                "causa nula nao pode sumir — ela e defeito de classificacao, e precisa aparecer");
    }
}
