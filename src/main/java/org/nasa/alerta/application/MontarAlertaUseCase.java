package org.nasa.alerta.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.alerta.domain.Cep;
import org.nasa.alerta.domain.DesastreProximo;
import org.nasa.alerta.domain.Email;
import org.nasa.alerta.domain.MensagemDeAlerta;
import org.nasa.alerta.domain.exceptions.CepSemPosicaoException;
import org.nasa.alerta.domain.ports.LeituraDeDesastresProximosPort;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o e-mail de alerta que a pessoa receberia — e <b>não guarda nada</b>.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o produto do sistema. Alguém informa o e-mail e o CEP,
 * e vê na hora a mensagem que seria enviada: quais desastres a NASA publicou perto dali, a
 * que distância, e há quanto tempo.</p>
 *
 * <p><b>NADA É PERSISTIDO — nem o e-mail, nem o alerta, nem a consulta.</b> É a decisão que
 * define esta fatia, e ela resolve três problemas de uma vez: não há lista de e-mails para
 * vazar, não há dado pessoal para proteger, e não há como abusar de um formulário que não
 * escreve. O ganho não planejado: os datasets exportados deste sistema nascem sanitizados,
 * porque não existe dado pessoal para sanitizar.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O e-mail nunca entra no corpo da mensagem nem em log.</b> Ele existe para a
 *       pessoa ver o alerta endereçado a ela na tela, e some no fim da requisição. Pôr um
 *       endereço dentro de um texto que se pode copiar, imprimir ou compartilhar é vazá-lo
 *       por outro caminho.</li>
 *   <li><b>Zero desastre é RESULTADO, e a mensagem diz isso com todas as letras.</b> É a
 *       resposta mais comum e a melhor notícia possível — mostrar uma lista vazia faria
 *       parecer erro de carregamento.</li>
 *   <li><b>CEP sem posição ABORTA, e não degrada.</b> É o oposto do que o cadastro fazia:
 *       lá a inscrição valia mesmo sem coordenada, porque podia ser corrigida depois. Aqui
 *       não há depois — sem posição não há alerta nenhum para mostrar, e fingir que há
 *       seria a pior resposta possível.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> CEP inexistente ou sem posição vira
 * {@link CepSemPosicaoException}, que a tela traduz em orientação — <b>qual</b> das duas
 * coisas falhou, porque uma pede corrigir o CEP e a outra não pede nada de quem consultou.
 * Provedor fora sobe como indisponibilidade, e a tela diz para tentar de novo.</p>
 */
@ApplicationScoped
public class MontarAlertaUseCase {

    private static final Logger LOG = Logger.getLogger(MontarAlertaUseCase.class);
    private static final String OPERACAO = "montar-alerta";

    /**
     * Raio padrão, em quilômetros.
     *
     * <p>100 km é a distância em que um desastre natural ainda é assunto de quem mora ali:
     * fumaça de incêndio florestal viaja mais que isso, e uma tempestade severa a 100 km
     * chega em horas.</p>
     */
    public static final double RAIO_PADRAO_KM = 100.0;

    public static final double RAIO_MINIMO_KM = 1.0;

    /** Metade da circunferência da Terra: acima disso "proximidade" não significa nada. */
    public static final double RAIO_MAXIMO_KM = 20_000.0;

    /**
     * Janela padrão, em dias.
     *
     * <p>Um desastre de dois anos atrás não é alerta, é história — e enchendo a mensagem com
     * ele o que importa fica invisível.</p>
     */
    public static final int DIAS_PADRAO = 30;

    /** Teto de itens na mensagem. Acima disso ninguém lê, e o primeiro é o que importa. */
    public static final int MAXIMO_NA_MENSAGEM = 20;

    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneOffset.UTC);

    @Inject
    ConsultarCepUseCase cep;

    @Inject
    LeituraDeDesastresProximosPort desastres;

    @Inject
    Relogio relogio;

    /**
     * Monta a mensagem.
     *
     * @param emailInformado só para a saudação na tela; <b>não é guardado nem registrado</b>
     * @param cepDigitado    de onde medir
     * @param raioKm         nulo usa {@link #RAIO_PADRAO_KM}
     * @param dias           nulo usa {@link #DIAS_PADRAO}
     */
    public MensagemDeAlerta executar(String emailInformado, String cepDigitado,
                                     Double raioKm, Integer dias) {
        // A validacao acontece nos objetos de valor, na construcao. `Email` recusa o que
        // nao e e-mail e `Cep` recusa o que nao tem oito digitos — validar de novo aqui
        // criaria duas regras para a mesma coisa, e elas divergem.
        Email destinatario = new Email(emailInformado);
        Cep onde = new Cep(cepDigitado);

        double raio = limitar(raioKm == null ? RAIO_PADRAO_KM : raioKm);
        int janela = Math.max(1, Math.min(dias == null ? DIAS_PADRAO : dias, 365));

        org.nasa.alerta.domain.ports.ConsultaCepPort.EnderecoDoCep achado =
                cep.executar(onde.digitos())
                .orElseThrow(() -> new CepSemPosicaoException(onde.digitos(), true));
        org.nasa.geo.domain.Coordenada posicao = achado.coordenada()
                // CEP EXISTE mas ninguem soube a posicao — e diferente de "CEP nao existe".
                // A tela precisa dizer qual dos dois foi: um pede corrigir, o outro nao
                // pede nada de quem consultou.
                .orElseThrow(() -> new CepSemPosicaoException(onde.digitos(), false));

        var agora = relogio.agora();
        var desde = agora.minus(Duration.ofDays(janela));
        List<DesastreProximo> encontrados = desastres.proximos(posicao, raio, desde, MAXIMO_NA_MENSAGEM);

        // O e-mail NAO entra no log. O CEP entra: ele identifica regiao, nao pessoa.
        LOG.info(Registro.de(OPERACAO, onde.digitos(),
                encontrados.size() + " desastre(s) em " + raio + " km / " + janela + " dias"));

        return new MensagemDeAlerta(
                assunto(encontrados, achado.localidade()),
                "Olá! Este é o alerta que você receberia por e-mail:",
                corpo(encontrados, raio, janela, achado.localidade()),
                encontrados,
                new MensagemDeAlerta.Local(onde.digitos(), descricaoDe(achado), posicao),
                raio,
                agora);
    }

    /**
     * A linha de assunto.
     *
     * <p><b>Ela nomeia o que foi encontrado</b>, e não "Alerta de desastres". Assunto
     * genérico é o que faz um alerta de verdade parecer o quinto e-mail promocional do dia —
     * e quem o vê no celular precisa decidir se abre sem abrir.</p>
     */
    private String assunto(List<DesastreProximo> achados, String cidade) {
        String lugar = (cidade == null || cidade.isBlank()) ? "sua região" : cidade;
        if (achados.isEmpty()) {
            return "Nenhum desastre natural perto de " + lugar;
        }
        var perto = achados.get(0);
        return achados.size() == 1
                ? "Alerta: " + perto.titulo() + " a " + perto.distanciaArredondadaKm()
                        + " km de " + lugar
                : "Alerta: " + achados.size() + " desastres perto de " + lugar
                        + " — o mais próximo a " + perto.distanciaArredondadaKm() + " km";
    }

    /** Os parágrafos, na ordem em que se lê. */
    private List<String> corpo(List<DesastreProximo> achados, double raio, int dias,
                               String cidade) {
        List<String> paragrafos = new ArrayList<>();
        String lugar = (cidade == null || cidade.isBlank()) ? "o endereço informado" : cidade;

        if (achados.isEmpty()) {
            // ZERO E RESULTADO, e a mensagem diz isso com todas as letras. Uma lista vazia
            // sem explicacao parece falha de carregamento — e neste sistema a diferenca
            // entre "nao ha desastre" e "nao consegui verificar" e tudo.
            paragrafos.add("Boas notícias: a NASA não publicou nenhum desastre natural a até "
                    + arredondar(raio) + " km de " + lugar + " nos últimos " + dias
                    + " dias.");
            paragrafos.add("Isto não é ausência de dados — é ausência de desastres. O "
                    + "sistema consultou a base e ela respondeu.");
            return paragrafos;
        }

        var perto = achados.get(0);
        paragrafos.add("A NASA publicou " + achados.size() + " desastre(s) natural(is) a até "
                + arredondar(raio) + " km de " + lugar + " nos últimos " + dias + " dias.");
        paragrafos.add("O mais próximo é " + perto.titulo() + ", a "
                + perto.distanciaArredondadaKm() + " km, registrado em "
                + QUANDO.format(perto.ocorridoEm()) + " (UTC)"
                + (perto.ativo() ? " e ainda em curso." : " e já encerrado."));

        long ativos = achados.stream().filter(d -> d.ativo()).count();
        if (ativos > 0 && achados.size() > 1) {
            paragrafos.add(ativos + " deste(s) evento(s) continua(m) em curso segundo a NASA.");
        }
        paragrafos.add("A distância é geodésica, medida sobre a curvatura da Terra — não é "
                + "uma caixa no mapa.");
        return paragrafos;
    }

    private String descricaoDe(org.nasa.alerta.domain.ports.ConsultaCepPort.EnderecoDoCep e) {
        StringBuilder sb = new StringBuilder();
        if (e.logradouro() != null && !e.logradouro().isBlank()) {
            sb.append(e.logradouro()).append(", ");
        }
        if (e.bairro() != null && !e.bairro().isBlank()) {
            sb.append(e.bairro()).append(", ");
        }
        sb.append(e.localidade() == null ? "" : e.localidade());
        if (e.uf() != null && !e.uf().isBlank()) {
            sb.append(" — ").append(e.uf());
        }
        return sb.toString().trim();
    }

    private static long arredondar(double km) {
        return Math.round(km);
    }

    private static double limitar(double raio) {
        return Math.max(RAIO_MINIMO_KM, Math.min(raio, RAIO_MAXIMO_KM));
    }
}
