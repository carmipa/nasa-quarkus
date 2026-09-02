package org.nasa.contato.presentation.web;

import org.nasa.contato.domain.Contato;

import java.util.List;

/**
 * O contato como a API devolve.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Mostra o dado nas duas formas que importam: como o
 * sistema guarda (só dígitos, e é a forma que torna dois telefones iguais realmente
 * iguais) e como a pessoa reconhece (pontuada).</p>
 *
 * <p><b>O CAMPO QUE NÃO EXISTIA NO LEGADO:</b> {@code recebeAlerta}, com
 * {@code motivoNaoRecebeAlerta} ao lado. No legado cadastrava-se um contato e nada dizia
 * se ele entraria na lista de avisos de desastre — dava para achar que a cobertura
 * existia. O silêncio aqui é o pior tipo, porque só se descobre no dia do evento.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> {@code criadoEm} sai em ISO-8601 UTC, terminando em
 * {@code Z}. Instante sem fuso nesta aplicação é o engano que custa mais caro.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Nenhum: só traduz o que já é válido.</p>
 */
public record ContatoResposta(Long id, String ddd, String telefone, String celular,
                              String whatsapp, String telefoneFormatado, String email,
                              String dominioDoEmail, String tipoContato, String tipoRotulo,
                              boolean recebeAlerta, String motivoNaoRecebeAlerta,
                              String criadoEm) {

    public static ContatoResposta de(Contato c) {
        return new ContatoResposta(
                c.id(), c.ddd(), c.telefone(), c.celular(), c.whatsapp(),
                c.contatoTelefonicoFormatado(),
                c.email().valor(), c.email().dominio(),
                c.tipo().name(), c.tipo().rotulo(),
                c.recebeAlerta(),
                c.recebeAlerta() ? null
                        : "so contatos do tipo EMERGENCIA recebem alerta de desastre",
                c.criadoEm() == null ? null : c.criadoEm().toString());
    }

    public static List<ContatoResposta> de(List<Contato> contatos) {
        return contatos.stream().map(ContatoResposta::de).toList();
    }
}
