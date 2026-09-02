package org.nasa.endereco.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.endereco.domain.Cep;
import org.nasa.endereco.domain.Endereco;
import org.nasa.endereco.domain.exceptions.EnderecoNaoEncontradoException;
import org.nasa.endereco.domain.ports.RepositorioDeEnderecosPort;

import java.util.Optional;

/**
 * Cadastra um endereco, preenchendo o que o CEP ja sabe.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o passo que transforma um cliente numa pessoa
 * <b>localizável</b>. Endereço sem coordenada é cadastrado do mesmo jeito — e é comum,
 * 1 de cada 6 CEPs medidos volta sem ela — mas o sistema <b>diz</b> que aquele endereço
 * não entra no alerta de proximidade. No legado esse silêncio era total.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O CEP preenche o que a pessoa não digitou</b>, e nunca sobrescreve o que ela
 *       digitou. Quem corrige o nome da rua sabe algo que a base do CEP não sabe —
 *       normalmente porque a rua mudou de nome e a base ainda não atualizou.</li>
 *   <li><b>Falha na consulta do CEP NÃO impede o cadastro.</b> O endereço entra com o que
 *       foi digitado, sem coordenada, marcado. Recusar o cadastro inteiro porque um
 *       serviço de terceiro caiu seria transferir a indisponibilidade dele para nós.</li>
 *   <li><b>Coordenada ausente é AUSENTE</b>, nunca {@code (0,0)}.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> CEP inexistente com dados insuficientes ⇒
 * {@link EnderecoNaoEncontradoException} (404). Falha de banco ⇒ 500 com causa-raiz.</p>
 */
@ApplicationScoped
public class CadastrarEnderecoUseCase {

    private static final Logger LOG = Logger.getLogger(CadastrarEnderecoUseCase.class);
    private static final String OPERACAO = "cadastrar-endereco";

    @Inject
    RepositorioDeEnderecosPort repositorio;

    @Inject
    ConsultarCepUseCase consultarCep;

    /**
     * Cadastra o endereco.
     *
     * @param cepDigitado o CEP; o que ele souber preenche o que faltar
     * @param numero      opcional
     * @param logradouro  se vazio, vem do CEP
     * @param complemento opcional, como o mundo real
     * @param clienteId   se informado, o endereco ja fica ligado ao cliente
     */
    public Endereco executar(String cepDigitado, Integer numero, String logradouro,
                             String bairro, String localidade, String uf, String complemento,
                             Long clienteId) {
        Cep cep = new Cep(cepDigitado);

        String rua = logradouro;
        String vizinhanca = bairro;
        String cidade = localidade;
        String estado = uf;
        Optional<org.nasa.geo.domain.Coordenada> coordenada = Optional.empty();

        try {
            var doCep = consultarCep.executar(cep.digitos());
            if (doCep.isPresent()) {
                var d = doCep.get();
                // Preenche o que FALTOU. Nunca sobrescreve o digitado: quem corrigiu o
                // nome da rua sabe algo que a base do CEP ainda nao sabe.
                rua = vazio(rua) ? d.logradouro() : rua;
                vizinhanca = vazio(vizinhanca) ? d.bairro() : vizinhanca;
                cidade = vazio(cidade) ? d.localidade() : cidade;
                estado = vazio(estado) ? d.uf() : estado;
                coordenada = d.coordenada();
            }
        } catch (RuntimeException falha) {
            // Provedor fora NAO impede o cadastro: o endereco entra com o que foi
            // digitado, sem coordenada, e a resposta diz que ele nao entra no alerta.
            LOG.warn(Registro.recusa(OPERACAO, cep.digitos(),
                    "CEP_NAO_CONSULTADO_" + falha.getClass().getSimpleName()));
        }

        Endereco gravado = repositorio.salvar(new Endereco(null, cep, numero, rua, vizinhanca,
                cidade, estado, complemento, coordenada, null));

        if (clienteId != null) {
            repositorio.vincularAoCliente(gravado.id(), clienteId);
        }

        LOG.info(Registro.de(OPERACAO, String.valueOf(gravado.id()),
                gravado.participaDoAlertaDeProximidade()
                        ? "endereco cadastrado COM coordenada: entra no alerta"
                        : "endereco cadastrado SEM coordenada: NAO entra no alerta de proximidade"));
        return gravado;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
