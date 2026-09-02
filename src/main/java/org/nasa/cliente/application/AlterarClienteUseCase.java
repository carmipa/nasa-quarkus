package org.nasa.cliente.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.Documento;
import org.nasa.cliente.domain.exceptions.ClienteNaoEncontradoException;
import org.nasa.cliente.domain.exceptions.DocumentoJaCadastradoException;
import org.nasa.cliente.domain.ports.RepositorioDeClientesPort;
import org.nasa.core.log.Registro;

import java.time.LocalDate;

/**
 * Altera os dados de um cliente já cadastrado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Nome muda, documento é corrigido, data vem errada da
 * digitação. Sem esta operação, o único conserto seria apagar e recadastrar — o que
 * levaria junto os endereços e os alertas já registrados da pessoa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O cliente tem de existir.</b> Alterar o que não existe é 404, não criação
 *       silenciosa: um {@code PUT} que cria registro é a forma mais rápida de encher a
 *       base de duplicatas a partir de link antigo.</li>
 *   <li><b>Trocar o documento não pode colidir com outro cliente</b> — e o "outro" é a
 *       parte que se esquece: o próprio cliente manter o mesmo documento é normal e
 *       precisa passar. Checar só "existe alguém com este documento" impediria salvar sem
 *       mudar nada, que é o que o operador faz o tempo todo ao corrigir só o nome.</li>
 *   <li><b>{@code criadoEm} não muda.</b> O passado não se reescreve porque o cadastro
 *       foi editado.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Cliente inexistente ⇒ 404. Documento de outra
 * pessoa ⇒ 409, com o documento na mensagem. O banco continua sendo a última linha: se
 * duas edições concorrentes tentarem o mesmo documento, uma delas será recusada lá.</p>
 */
@ApplicationScoped
public class AlterarClienteUseCase {

    private static final Logger LOG = Logger.getLogger(AlterarClienteUseCase.class);

    @Inject
    RepositorioDeClientesPort repositorio;

    public Cliente executar(long id, String nome, String sobrenome,
                            LocalDate nascimento, String documento) {
        Cliente atual = repositorio.porId(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("id=" + id));

        Documento novoDoc = new Documento(documento);

        // A colisão só existe quando o documento é de OUTRO cliente. Manter o próprio
        // documento é o caso normal de "corrigi só o nome" — e recusá-lo seria impedir
        // a edição mais comum que existe.
        repositorio.porDocumento(novoDoc).ifPresent(dono -> {
            if (!dono.id().equals(atual.id())) {
                LOG.warn(Registro.recusa("alterar-cliente", novoDoc.digitos(),
                        "DOCUMENTO_DE_OUTRO_CLIENTE"));
                throw new DocumentoJaCadastradoException(novoDoc.formatado());
            }
        });

        Cliente alterado = repositorio.atualizar(atual.com(nome, sobrenome, nascimento, novoDoc));
        LOG.info(Registro.de("alterar-cliente", String.valueOf(id),
                "cliente alterado: " + alterado.nomeCompleto()));
        return alterado;
    }
}
