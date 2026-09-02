package org.nasa.contato.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.Email;
import org.nasa.contato.domain.TipoContato;
import org.nasa.contato.domain.exceptions.EmailJaCadastradoException;
import org.nasa.contato.domain.ports.RepositorioDeContatosPort;
import org.nasa.core.log.Registro;

/**
 * Cadastra um contato.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É por aqui que entra o destino do alerta. Um contato
 * cadastrado errado não produz erro nenhum — produz silêncio na hora do desastre.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O e-mail é único, e quem garante é o BANCO.</b> A consulta prévia daqui é
 *       amistosa, não é a proteção: entre a pergunta e a inserção cabe outra requisição,
 *       e clique duplo é o caso comum. A restrição {@code contato_email_unico} é a que
 *       vale para duas abas, dois aparelhos e duas pessoas ao mesmo tempo.</li>
 *   <li><b>O tipo decide quem recebe alerta</b>, e o log registra quando um contato de
 *       EMERGENCIA é criado. Alguém entrar na lista de avisos de desastre é um fato que
 *       merece rastro — inclusive para responder depois "por que esta pessoa recebeu?".</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> E-mail repetido ⇒
 * {@link EmailJaCadastradoException} (409). E-mail malformado ou campo torto sobem do
 * domínio como {@code DADO_INVALIDO} (400). Falha de banco vira 500 com causa-raiz.</p>
 */
@ApplicationScoped
public class CadastrarContatoUseCase {

    private static final Logger LOG = Logger.getLogger(CadastrarContatoUseCase.class);
    private static final String OPERACAO = "cadastrar-contato";

    @Inject
    RepositorioDeContatosPort repositorio;

    public Contato executar(String ddd, String telefone, String celular, String whatsapp,
                            String email, String tipoContato) {
        Email endereco = new Email(email);
        TipoContato tipo = TipoContato.de(tipoContato);

        // Checagem amistosa. NAO e a protecao — a protecao e a restricao do banco.
        if (repositorio.porEmail(endereco).isPresent()) {
            LOG.warn(Registro.recusa(OPERACAO, endereco.valor(), "EMAIL_JA_CADASTRADO"));
            throw new EmailJaCadastradoException(endereco.valor());
        }

        Contato gravado = repositorio.salvar(
                Contato.novo(ddd, telefone, celular, whatsapp, endereco, tipo));

        LOG.info(Registro.de(OPERACAO, String.valueOf(gravado.id()),
                "contato cadastrado, tipo=" + gravado.tipo()
                        + (gravado.recebeAlerta() ? " RECEBE ALERTA" : "")));
        return gravado;
    }
}
