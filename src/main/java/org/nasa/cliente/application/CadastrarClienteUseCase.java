package org.nasa.cliente.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.Documento;
import org.nasa.cliente.domain.exceptions.DocumentoJaCadastradoException;
import org.nasa.cliente.domain.ports.RepositorioDeClientesPort;
import org.nasa.core.log.Registro;

import java.time.LocalDate;

/**
 * Cadastra uma pessoa para receber alerta.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a porta de entrada do sistema: sem cadastro não há
 * quem avisar. E é a operação em que um erro custa mais caro depois — cadastro duplicado
 * significa endereços espalhados entre dois registros, e alerta indo para metade deles.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-CLIENTE-001 — um documento, um cliente.</b> A checagem prévia aqui existe
 *       para dar mensagem boa; <b>quem garante é o {@code UNIQUE} do banco</b>. Entre o
 *       "já existe?" e o {@code INSERT} cabe outra requisição — e o clique duplo é
 *       justamente o caso comum, não o raro.</li>
 *   <li><b>O documento é normalizado antes de comparar</b> (só dígitos), senão
 *       {@code "111.222.333-44"} e {@code "11122233344"} viram duas pessoas — que era o
 *       comportamento do legado.</li>
 *   <li><b>Depende só de porta.</b> Este caso de uso roda em teste sem banco nenhum.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Documento repetido ⇒
 * {@link DocumentoJaCadastradoException} (vira 409). Campo inválido ⇒ a exceção do próprio
 * domínio, lançada na construção (vira 400). Nenhuma das duas é 500: são respostas
 * previsíveis a pedidos previsíveis, e o log as registra como recusa com motivo, não como
 * erro do sistema.</p>
 */
@ApplicationScoped
public class CadastrarClienteUseCase {

    private static final Logger LOG = Logger.getLogger(CadastrarClienteUseCase.class);

    @Inject
    RepositorioDeClientesPort repositorio;

    public Cliente executar(String nome, String sobrenome, LocalDate nascimento, String documento) {
        Documento doc = new Documento(documento);

        // Checagem amistosa. NÃO é a proteção — a proteção é a constraint.
        if (repositorio.existeComDocumento(doc)) {
            LOG.warn(Registro.recusa("cadastrar-cliente", doc.digitos(), "DOCUMENTO_JA_CADASTRADO"));
            throw new DocumentoJaCadastradoException(doc.formatado());
        }

        Cliente gravado = repositorio.salvar(Cliente.novo(nome, sobrenome, nascimento, doc));
        LOG.info(Registro.de("cadastrar-cliente", String.valueOf(gravado.id()),
                "cliente cadastrado: " + gravado.nomeCompleto()));
        return gravado;
    }
}
