package org.nasa.contato.presentation.web;

/**
 * O contato como a API recebe.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa o que ENTRA do que a fatia guarda. Sem este
 * tipo, o cliente da API poderia mandar {@code id} ou {@code criadoEm} e esperar que
 * valessem — e um deles valeria, algum dia, por descuido de mapeamento.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Tudo chega como texto e nada é validado aqui. A
 * validação é do domínio, uma vez só: {@code Email} decide o que é e-mail,
 * {@code TipoContato} decide o que é tipo, {@code Contato} decide o que é telefone.
 * Validar também aqui criaria uma segunda regra, que diverge da primeira no dia em que
 * uma das duas mudar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Nenhum: este tipo não falha. Campo torto vira
 * exceção quando o domínio o recebe, com o nome do campo no alvo.</p>
 *
 * @param ddd         dois dígitos, opcional
 * @param telefone    fixo, opcional
 * @param celular     celular, opcional
 * @param whatsapp    WhatsApp, opcional
 * @param email       obrigatório: o único canal garantido
 * @param tipoContato PRINCIPAL, ALTERNATIVO, EMERGENCIA ou COMERCIAL; vazio vira PRINCIPAL
 */
public record ContatoPedido(String ddd, String telefone, String celular, String whatsapp,
                            String email, String tipoContato) {
}
