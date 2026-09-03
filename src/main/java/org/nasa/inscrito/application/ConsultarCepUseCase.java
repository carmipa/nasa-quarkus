package org.nasa.inscrito.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.log.Registro;
import org.nasa.inscrito.domain.Cep;
import org.nasa.inscrito.domain.exceptions.ProvedorDeEnderecoIndisponivelException;
import org.nasa.inscrito.domain.ports.CadeiaDeProvedoresDeCepPort;
import org.nasa.inscrito.domain.ports.ConsultaCepPort;
import org.nasa.inscrito.domain.ports.GeocodificacaoPort;
import org.nasa.geo.domain.Coordenada;

import java.util.List;
import java.util.Optional;

/**
 * Descobre o endereço de um CEP — e a coordenada dele, quando dá.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a operação que transforma oito dígitos digitados em
 * um lugar no mapa. Dela depende o alerta inteiro: endereço sem coordenada nunca gera
 * aviso de proximidade.</p>
 *
 * <p><b>A CADEIA, E POR QUE ELA TEM ESTA ORDEM</b> (medido em 2026-09-02):</p>
 * <ol>
 *   <li><b>BrasilAPI</b> — 0,23 s, e traz endereço <b>e</b> coordenada na mesma resposta;</li>
 *   <li><b>ViaCEP</b> — 1,04 s, reserva, sem coordenada;</li>
 *   <li><b>Nominatim</b> — só quando a coordenada não veio. É 1 de cada 6 CEPs.</li>
 * </ol>
 *
 * <p>O legado fazia <b>sempre</b> duas chamadas (ViaCEP + Google), porque o ViaCEP não
 * devolve lat/lon. Aqui a segunda só acontece quando é necessária.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>CEP inexistente é {@link Optional#empty()}</b>; provedor fora é <b>exceção</b>.
 *       Confundir os dois faz a pessoa apagar um CEP que estava certo.</li>
 *   <li><b>Só vira indisponibilidade quando TODOS falham.</b> Um provedor fora cai para o
 *       seguinte, com log de recusa e motivo — degradação declarada.</li>
 *   <li><b>A geocodificação é BEST EFFORT.</b> Se ela falhar, o endereço volta sem
 *       coordenada em vez de a consulta inteira falhar: perder a coordenada custa o
 *       alerta de proximidade daquele endereço; perder o endereço custa o cadastro.</li>
 *   <li><b>Coordenada ausente é ausente.</b> Nunca {@code (0,0)}.</li>
 *   <li><b>A ordem dos provedores é DECLARADA</b>, não deduzida da ordem em que o CDI
 *       resolve os beans — que não é garantida e mudaria o comportamento sem ninguém
 *       tocar em nada.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Todos os provedores fora ⇒
 * {@link ProvedorDeEnderecoIndisponivelException} (503, "tente de novo"). Toda tentativa
 * falha é <b>contada</b> na telemetria por causa-raiz, e é essa contagem que responde
 * depois se o problema foi rede, formato ou bloqueio por política de uso.</p>
 */
@ApplicationScoped
public class ConsultarCepUseCase {

    private static final Logger LOG = Logger.getLogger(ConsultarCepUseCase.class);
    private static final String OPERACAO = "consultar-cep";

    @Inject
    CadeiaDeProvedoresDeCepPort cadeia;

    @Inject
    GeocodificacaoPort geocodificacao;

    /**
     * A telemetria TRANSVERSAL, do kernel.
     *
     * <p>Antes havia uma telemetria própria da fatia de endereço — um arquivo JSON com
     * escrita atômica, que contava causas-raiz. Ela saiu com a fatia, e a substituta é
     * melhor: o coletor do kernel mede <b>todas</b> as operações do sistema no mesmo
     * vocabulário, e a tela de telemetria já as mostra lado a lado. Duas contagens
     * separadas do mesmo tipo de coisa divergem, e a mais esquecida vira a errada.</p>
     */
    @Inject
    org.nasa.core.telemetria.Telemetria telemetria;

    /**
     * A ordem dos provedores — <b>declarada</b>, não deduzida da resolução de beans.
     *
     * <p>Injetar {@code Instance<ConsultaCepPort>} e iterar pareceria mais elegante e
     * seria um defeito com data marcada: a ordem em que o CDI resolve implementações
     * <b>não é garantida</b>, e o provedor primário poderia virar o reserva sem ninguém
     * tocar em nada — trocando 0,23 s por 1,04 s em toda consulta, silenciosamente.</p>
     *
     * <p>Quem conhece os provedores é {@code CadeiaDeProvedoresDeCepPort}, implementada na
     * infraestrutura. A primeira versão injetava os adaptadores direto aqui, e a guarda de
     * fronteira <b>reprovou o build</b> — corretamente: {@code application} não depende de
     * {@code infrastructure}, e ceder nisso custaria a testabilidade sem rede.</p>
     *
     * <p>Deixado {@code protected} de propósito: é a costura que permite ao teste montar
     * a cadeia com dublês e provar o comportamento sem rede.</p>
     */
    protected List<ConsultaCepPort> provedores() {
        return cadeia.emOrdem();
    }

    public Optional<ConsultaCepPort.EnderecoDoCep> executar(String cepDigitado) {
        Cep cep = new Cep(cepDigitado);
        boolean algumRespondeu = false;
        ErroDePipeline ultimaFalha = null;

        for (ConsultaCepPort provedor : provedores()) {
            try {
                Optional<ConsultaCepPort.EnderecoDoCep> achado = provedor.consultar(cep);
                algumRespondeu = true;
                if (achado.isEmpty()) {
                    // Este provedor respondeu e disse que o CEP não existe. Continuar para
                    // o próximo: bases de CEP divergem, e um pode conhecer o que o outro
                    // não conhece.
                    LOG.info(Registro.recusa(OPERACAO, cep.digitos(),
                            "NAO_ENCONTRADO_em_" + provedor.nome()));
                    continue;
                }
                return Optional.of(completarCoordenada(achado.get()));
            } catch (ErroDePipeline falha) {
                // Degradação DECLARADA: cai para o próximo, contando a causa.
                ultimaFalha = falha;
                telemetria.falha(OPERACAO, null);
                LOG.warn(Registro.recusa(OPERACAO, cep.digitos(),
                        provedor.nome() + "_" + falha.causaRaiz().name()));
            }
        }

        if (!algumRespondeu) {
            // NENHUM provedor respondeu: isto é indisponibilidade, não "CEP inexistente".
            throw new ProvedorDeEnderecoIndisponivelException(cep.digitos(), ultimaFalha);
        }
        return Optional.empty();   // todos responderam, e o CEP realmente não existe
    }

    /**
     * Completa a coordenada quando o provedor de CEP não trouxe.
     *
     * <p><b>Best effort de propósito:</b> falha aqui devolve o endereço <b>sem</b>
     * coordenada, e não derruba a consulta. O endereço ainda serve para cadastro,
     * correspondência e conferência — só não entra no alerta de proximidade, e a tela diz
     * isso em voz alta.</p>
     */
    private ConsultaCepPort.EnderecoDoCep completarCoordenada(ConsultaCepPort.EnderecoDoCep base) {
        if (base.coordenada().isPresent()) {
            return base;
        }
        String textoDaBusca = base.logradouro() + ", " + base.localidade() + " - " + base.uf()
                + ", " + base.cep().formatado();
        try {
            Optional<Coordenada> encontrada = geocodificacao.geocodificar(textoDaBusca);
            if (encontrada.isEmpty()) {
                LOG.info(Registro.recusa(OPERACAO, base.cep().digitos(), "GEOCODIFICACAO_SEM_RESULTADO"));
            }
            return new ConsultaCepPort.EnderecoDoCep(base.cep(), base.logradouro(), base.bairro(),
                    base.localidade(), base.uf(), encontrada, base.provedor());
        } catch (ErroDePipeline falha) {
            telemetria.falha(OPERACAO, null);
            LOG.warn(Registro.recusa(OPERACAO, base.cep().digitos(),
                    "GEOCODIFICACAO_" + falha.causaRaiz().name()));
            return base;   // sem coordenada, e declarado — nunca (0,0)
        } catch (RuntimeException inesperada) {
            telemetria.falha(OPERACAO, null);
            LOG.warn(Registro.recusa(OPERACAO, base.cep().digitos(),
                    CausaRaiz.NAO_CLASSIFICADA.name()), inesperada);
            return base;
        }
    }
}
