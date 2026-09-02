package org.nasa.cliente.presentation.web;

import org.nasa.cliente.domain.Cliente;

/**
 * O cliente como a API devolve.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar o que sai na resposta do que existe no domínio.
 * É o que permite mudar o domínio sem quebrar quem consome a API — e o que impede um
 * campo interno de vazar para fora só porque alguém o acrescentou ao record.</p>
 *
 * <p><b>INVARIANTES.</b></p>
 * <ol>
 *   <li><b>O documento sai FORMATADO</b> ({@code 111.222.333-44}), porque é assim que a
 *       pessoa reconhece o próprio documento — mas é guardado só com dígitos, que é o que
 *       faz a unicidade funcionar. As duas formas têm donos diferentes de propósito.</li>
 *   <li><b>Instantes em ISO-8601 UTC</b>, como todo o resto do sistema.</li>
 * </ol>
 *
 * <p><b>FALHA.</b> Não falha: é conversão de um objeto já válido.</p>
 */
public record ClienteResposta(Long id, String nome, String sobrenome, String nomeCompleto,
                              String dataNascimento, String documento, String documentoFormatado,
                              String criadoEm) {

    public static ClienteResposta de(Cliente c) {
        return new ClienteResposta(
                c.id(),
                c.nome(),
                c.sobrenome(),
                c.nomeCompleto(),
                c.dataNascimento().toString(),
                c.documento().digitos(),
                c.documento().formatado(),
                c.criadoEm() == null ? null : c.criadoEm().toString());
    }
}
