package org.nasa.inscrito.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.geo.domain.Coordenada;
import org.nasa.inscrito.domain.Cep;
import org.nasa.inscrito.domain.Email;
import org.nasa.inscrito.domain.Inscrito;
import org.nasa.inscrito.domain.ports.RepositorioDeInscritosPort;

import java.util.Optional;

/**
 * Inscreve alguém para receber alerta quando houver desastre perto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a porta de entrada do sistema. A pessoa informa nome,
 * e-mail e CEP; o sistema descobre a coordenada e passa a vigiar aquele ponto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A inscrição é gravada MESMO SEM COORDENADA.</b> O CEP vira coordenada por
 *       provedores externos, e eles falham. Recusar a inscrição porque o Nominatim estava
 *       fora seria punir a pessoa por uma falha nossa — e ela não voltaria. A coordenada
 *       ausente é <b>marcada e visível</b>, e pode ser preenchida depois.</li>
 *   <li><b>E-mail repetido é RECUSA, não falha.</b> A pessoa já está na lista, e o sistema
 *       funcionou. A garantia é do banco, não de um {@code SELECT} antes: entre o
 *       {@code SELECT} e o {@code INSERT} cabe o segundo clique — que é justamente o caso
 *       que se quer cobrir.</li>
 *   <li><b>Nada aqui envia nada.</b> Inscrever cria o direito de ser avisado; quem avisa é
 *       a varredura de alertas. Misturar as duas coisas faria uma falha de envio derrubar
 *       um cadastro que estava correto.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Dado inválido é recusado antes de tocar o banco,
 * nomeando o campo. Provedor de CEP fora <b>degrada</b>: a inscrição entra sem coordenada,
 * com registro em WARN — nunca em silêncio.</p>
 */
@ApplicationScoped
public class InscreverUseCase {

    private static final Logger LOG = Logger.getLogger(InscreverUseCase.class);
    private static final String OPERACAO = "inscrever";

    @Inject
    RepositorioDeInscritosPort repositorio;

    @Inject
    ConsultarCepUseCase cep;

    @Inject
    Relogio relogio;

    /**
     * O que a inscrição produziu.
     *
     * @param inscrito       o que ficou gravado
     * @param achouPosicao   se o CEP virou coordenada. {@code false} significa que a
     *                       inscrição existe mas <b>ainda não recebe alerta de
     *                       proximidade</b> — e a tela diz isso, nunca esconde
     * @param motivoSemPosicao por que não achou, quando não achou
     */
    public record Resultado(Inscrito inscrito, boolean achouPosicao,
                            String motivoSemPosicao) {
    }

    public Resultado executar(String nome, String email, String telefone, String cepDigitado,
                              Double raioKm) {
        // A validacao acontece nos objetos de valor, na construcao: `Email` recusa o que
        // nao e e-mail, `Cep` recusa o que nao tem oito digitos. Validar aqui de novo
        // criaria duas regras para a mesma coisa, e elas divergem.
        Email destinatario = new Email(email);
        Cep onde = new Cep(cepDigitado);
        double raio = raioKm == null ? Inscrito.RAIO_PADRAO_KM : raioKm;

        Coordenada posicao = null;
        String motivo = null;
        try {
            var achado = cep.executar(onde.digitos());
            if (achado.isEmpty()) {
                motivo = "o CEP nao foi encontrado em nenhum provedor";
            } else if (achado.get().coordenada().isEmpty()) {
                // O CEP existe, mas nenhum provedor soube a posicao. E diferente de "CEP
                // nao existe", e a tela precisa dizer qual dos dois foi: um pede corrigir
                // o CEP, o outro nao pede nada de quem se inscreveu.
                motivo = "o CEP existe, mas nenhum provedor soube a posicao dele";
            } else {
                posicao = achado.get().coordenada().get();
            }
        } catch (RuntimeException provedorFora) {
            // DEGRADA, nao aborta. A inscricao vale mais que a coordenada: ela pode ser
            // preenchida depois, e a pessoa que desistiu de se inscrever nao volta.
            motivo = "os provedores de CEP nao responderam";
            LOG.warn(Registro.recusa(OPERACAO, onde.digitos(), "SEM_COORDENADA_PROVEDOR_FORA"),
                    provedorFora);
        }

        var novo = Inscrito.novo(nome, destinatario, telefone, onde, posicao, raio,
                relogio.agora());
        var gravado = repositorio.gravar(novo);

        if (posicao == null) {
            // Registrado SEMPRE que falta posicao — inclusive quando o provedor respondeu
            // e simplesmente nao sabia o CEP. Sem esta linha, uma inscricao que nunca vai
            // receber alerta some no meio das que funcionam.
            LOG.warn(Registro.recusa(OPERACAO, String.valueOf(gravado.id()),
                    "INSCRITO_SEM_COORDENADA"));
        } else {
            LOG.info(Registro.de(OPERACAO, String.valueOf(gravado.id()),
                    "inscrito com posicao, raio de " + raio + " km"));
        }
        return new Resultado(gravado, posicao != null, motivo);
    }
}
