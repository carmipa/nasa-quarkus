package org.nasa.endereco.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.endereco.domain.Cep;
import org.nasa.endereco.domain.exceptions.ProvedorDeEnderecoIndisponivelException;
import org.nasa.endereco.domain.ports.ConsultaCepPort;
import org.nasa.endereco.domain.ports.GeocodificacaoPort;
import org.nasa.endereco.domain.ports.TelemetriaEnderecoPort;
import org.nasa.endereco.domain.TelemetriaEndereco;
import org.nasa.geo.domain.Coordenada;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da cadeia de provedores — e das quatro distinções que ela precisa manter.
 *
 * <p><b>PROPÓSITO.</b> Este caso de uso existe para não confundir quatro coisas que
 * terminam parecidas na tela: <b>o CEP não existe</b>, <b>o provedor caiu</b>, <b>o CEP
 * existe mas sem coordenada</b> e <b>a geocodificação falhou</b>. Cada uma exige uma
 * reação diferente de quem opera, e trocá-las custa caro: dizer "CEP não encontrado"
 * quando o provedor caiu faz a pessoa apagar um CEP que estava certo.</p>
 *
 * <p>Tudo aqui roda com dublê, <b>sem rede</b> — que é o que a arquitetura de portas
 * compra.</p>
 */
@DisplayName("ConsultarCepUseCase — a cadeia de provedores e as quatro distincoes")
class ConsultarCepUseCaseTest {

    private static final Cep CEP = new Cep("01310200");

    /** Provedor de mentira, programável. */
    static final class ProvedorFalso implements ConsultaCepPort {
        private final String nome;
        private final Optional<EnderecoDoCep> resposta;
        private final RuntimeException falha;
        int chamadas;

        ProvedorFalso(String nome, Optional<EnderecoDoCep> resposta, RuntimeException falha) {
            this.nome = nome;
            this.resposta = resposta;
            this.falha = falha;
        }

        static ProvedorFalso queResponde(String nome, Optional<Coordenada> coord) {
            return new ProvedorFalso(nome, Optional.of(new EnderecoDoCep(CEP, "Av Paulista",
                    "Bela Vista", "Sao Paulo", "SP", coord, nome)), null);
        }

        static ProvedorFalso queNaoAcha(String nome) {
            return new ProvedorFalso(nome, Optional.empty(), null);
        }

        static ProvedorFalso queCai(String nome) {
            return new ProvedorFalso(nome, null,
                    new ProvedorDeEnderecoIndisponivelException(nome, null));
        }

        @Override public Optional<EnderecoDoCep> consultar(Cep cep) {
            chamadas++;
            if (falha != null) {
                throw falha;
            }
            return resposta;
        }

        @Override public String nome() { return nome; }
    }

    /** Telemetria de mentira: guarda as causas contadas. */
    static final class TelemetriaFalsa implements TelemetriaEnderecoPort {
        final List<CausaRaiz> causas = new ArrayList<>();
        @Override public void registrar(TelemetriaEndereco evento) { }
        @Override public Optional<TelemetriaEndereco> ultimo(String operacao) { return Optional.empty(); }
        @Override public void contar(CausaRaiz causa) { causas.add(causa); }
    }

    private static ConsultarCepUseCase montar(ProvedorFalso umProvedor, ProvedorFalso outroProvedor,
                                              GeocodificacaoPort geo, TelemetriaFalsa tel) {
        // Sem subclasse anonima: a cadeia e uma PORTA, e o teste a injeta como lambda.
        // Assim o teste exercita o caminho REAL do codigo (`provedores()` lendo a porta),
        // em vez de substituir o proprio metodo que se quer provar.
        var uc = new ConsultarCepUseCase();
        uc.cadeia = () -> List.of(umProvedor, outroProvedor);
        uc.geocodificacao = geo;
        uc.telemetria = tel;
        return uc;
    }

    @Test
    @DisplayName("o primario responde com coordenada: a reserva NEM E CHAMADA")
    void primarioResolveSozinho() {
        var primario = ProvedorFalso.queResponde("brasilapi",
                Optional.of(new Coordenada(-23.56, -46.65)));
        var reserva = ProvedorFalso.queResponde("viacep", Optional.empty());
        var geo = (GeocodificacaoPort) t -> { throw new AssertionError("geocodificacao nao devia ser chamada"); };

        var achado = montar(primario, reserva, geo, new TelemetriaFalsa()).executar("01310-200");

        assertTrue(achado.isPresent());
        assertTrue(achado.get().coordenada().isPresent());
        assertEquals(1, primario.chamadas);
        assertEquals(0, reserva.chamadas, "chamar a reserva sem precisar gasta tempo e cota");
    }

    @Test
    @DisplayName("primario CAI: cai para a reserva, e conta a causa na telemetria")
    void caiParaAReserva() {
        var primario = ProvedorFalso.queCai("brasilapi");
        var reserva = ProvedorFalso.queResponde("viacep", Optional.empty());
        var tel = new TelemetriaFalsa();
        var geo = (GeocodificacaoPort) t -> Optional.of(new Coordenada(-23.56, -46.65));

        var achado = montar(primario, reserva, geo, tel).executar("01310-200");

        assertTrue(achado.isPresent(), "a reserva tinha o endereco");
        assertEquals("viacep", achado.get().provedor());
        assertTrue(tel.causas.contains(CausaRaiz.PROVEDOR_INDISPONIVEL),
                "a queda do primario precisa ser CONTADA, nao so registrada");
    }

    @Test
    @DisplayName("TODOS caem: e INDISPONIBILIDADE (503), nunca 'CEP nao existe' (404)")
    void todosCaemViraIndisponibilidade() {
        var tel = new TelemetriaFalsa();
        var uc = montar(ProvedorFalso.queCai("brasilapi"), ProvedorFalso.queCai("viacep"),
                t -> Optional.empty(), tel);

        var erro = assertThrows(ProvedorDeEnderecoIndisponivelException.class,
                () -> uc.executar("01310-200"));

        System.out.println("[CEP] " + erro.linhaDeLog());
        assertEquals(CausaRaiz.PROVEDOR_INDISPONIVEL, erro.causaRaiz(),
                "dizer 'CEP nao encontrado' aqui faria a pessoa apagar um CEP correto");
        assertEquals(2, tel.causas.size(), "as duas quedas contam");
    }

    @Test
    @DisplayName("todos RESPONDEM e nenhum acha: e vazio (404), nao excecao")
    void todosRespondemENenhumAcha() {
        var uc = montar(ProvedorFalso.queNaoAcha("brasilapi"), ProvedorFalso.queNaoAcha("viacep"),
                t -> Optional.empty(), new TelemetriaFalsa());

        assertTrue(uc.executar("01310-200").isEmpty(),
                "CEP inexistente e resposta legitima, nao falha do sistema");
    }

    @Test
    @DisplayName("sem coordenada no CEP: a geocodificacao entra e completa")
    void geocodificacaoCompletaOQueFaltou() {
        // O caso real: 1 de cada 6 CEPs medidos volta sem coordenada.
        var primario = ProvedorFalso.queResponde("brasilapi", Optional.empty());
        var geo = (GeocodificacaoPort) t -> Optional.of(new Coordenada(-9.97, -67.8));

        var achado = montar(primario, ProvedorFalso.queNaoAcha("viacep"), geo, new TelemetriaFalsa())
                .executar("69900-000");

        assertTrue(achado.get().coordenada().isPresent(), "a geocodificacao devia ter completado");
        assertEquals(-9.97, achado.get().coordenada().get().latitude(), 0.001);
    }

    @Test
    @DisplayName("geocodificacao FALHA: devolve o endereco SEM coordenada, sem derrubar a consulta")
    void geocodificacaoFalhaNaoDerrubaAConsulta() {
        var primario = ProvedorFalso.queResponde("brasilapi", Optional.empty());
        var tel = new TelemetriaFalsa();
        GeocodificacaoPort geoQuebrado = t -> {
            throw new org.nasa.endereco.infrastructure.adapters
                    .ProvedorDeGeocodificacaoIndisponivelException(t, null);
        };

        var achado = montar(primario, ProvedorFalso.queNaoAcha("viacep"), geoQuebrado, tel)
                .executar("69900-000");

        assertTrue(achado.isPresent(), "perder a coordenada nao pode custar o endereco inteiro");
        assertFalse(achado.get().coordenada().isPresent(), "e NUNCA (0,0)");
        assertTrue(tel.causas.contains(CausaRaiz.PROVEDOR_INDISPONIVEL),
                "a falha da geocodificacao precisa aparecer na contagem");
    }
}
