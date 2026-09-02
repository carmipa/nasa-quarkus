package org.nasa.contato.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.Email;
import org.nasa.contato.domain.TipoContato;
import org.nasa.contato.domain.exceptions.ContatoNaoEncontradoException;
import org.nasa.contato.domain.exceptions.EmailJaCadastradoException;
import org.nasa.contato.domain.ports.RepositorioDeContatosPort;
import org.nasa.core.log.Registro;

/**
 * Altera um contato existente.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Corrigir telefone, trocar e-mail ou mudar o tipo. A
 * mudança de tipo é a que tem consequência operacional: promover a EMERGENCIA inscreve
 * alguém nos avisos de desastre, e rebaixar tira essa pessoa da lista.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Manter o MESMO e-mail é o caso normal, e não pode ser recusado.</b> Uma
 *       checagem ingênua do tipo "já existe alguém com este e-mail?" reprovaria a
 *       alteração mais comum de todas — corrigir o telefone sem mexer no e-mail —,
 *       porque quem existe é o próprio contato sendo alterado. A comparação é por
 *       identificador, não por e-mail.</li>
 *   <li><b>Mudança de tipo que entra ou sai de EMERGENCIA é registrada em WARN.</b> É a
 *       única alteração daqui cuja consequência é alguém deixar de ser avisado — e o
 *       sintoma disso é silêncio, meses depois, num dia ruim.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Contato inexistente ⇒
 * {@link ContatoNaoEncontradoException} (404). E-mail já usado por OUTRO contato ⇒
 * {@link EmailJaCadastradoException} (409). Campo torto sobe do domínio como 400.</p>
 */
@ApplicationScoped
public class AlterarContatoUseCase {

    private static final Logger LOG = Logger.getLogger(AlterarContatoUseCase.class);
    private static final String OPERACAO = "alterar-contato";

    @Inject
    RepositorioDeContatosPort repositorio;

    public Contato executar(long id, String ddd, String telefone, String celular,
                            String whatsapp, String email, String tipoContato) {
        Contato existente = repositorio.porId(id)
                .orElseThrow(() -> new ContatoNaoEncontradoException(String.valueOf(id)));

        Email endereco = new Email(email);
        TipoContato tipo = TipoContato.de(tipoContato);

        // O MESMO e-mail no MESMO contato tem de passar. Comparar por identificador, e
        // nao por e-mail, e o que separa "ja existe" de "ja e voce".
        repositorio.porEmail(endereco).ifPresent(dono -> {
            if (!dono.id().equals(id)) {
                LOG.warn(Registro.recusa(OPERACAO, endereco.valor(), "EMAIL_DE_OUTRO_CONTATO"));
                throw new EmailJaCadastradoException(endereco.valor());
            }
        });

        Contato salvo = repositorio.atualizar(
                existente.com(ddd, telefone, celular, whatsapp, endereco, tipo));

        if (existente.recebeAlerta() != salvo.recebeAlerta()) {
            LOG.warn(Registro.de(OPERACAO, String.valueOf(id),
                    salvo.recebeAlerta()
                            ? "PASSOU a receber alerta de desastre (tipo " + salvo.tipo() + ")"
                            : "DEIXOU de receber alerta de desastre (tipo " + salvo.tipo() + ")"));
        } else {
            LOG.info(Registro.de(OPERACAO, String.valueOf(id), "contato alterado"));
        }
        return salvo;
    }
}
