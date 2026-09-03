package org.nasa.alerta.domain;

import org.nasa.alerta.domain.exceptions.EmailInvalidoException;

/**
 * Um endereço de e-mail — o único canal garantido de um contato.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Telefone, celular e WhatsApp são opcionais; o e-mail
 * não. É por ele que o sistema avisa quando um evento natural acontece perto do endereço
 * de um cliente. Existir como <b>tipo</b>, e não como {@code String}, é o que impede que
 * um endereço nunca validado atravesse a aplicação até a hora de enviar o alerta — que é
 * o pior momento possível para descobrir que ele estava torto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Guardado em minúsculas e sem espaços nas pontas.</b> "Ana@Exemplo.com " e
 *       "ana@exemplo.com" são a mesma caixa postal no mundo real; normalizar aqui é o
 *       que faz a unicidade do banco enxergá-las como o mesmo contato. Sem isto,
 *       cadastrar as duas formas cria dois contatos e o alerta sai duplicado.</li>
 *   <li><b>A validação é grosseira DE PROPÓSITO:</b> arroba única, com algo antes e algo
 *       depois, e um ponto no domínio. Expressão elaborada de e-mail é conhecida por
 *       recusar endereços válidos raros enquanto aceita os inválidos comuns — e um
 *       cadastro recusado por rigor inventado custa mais que um endereço torto, que a
 *       primeira mensagem devolvida revela.</li>
 *   <li><b>Nunca decide se o endereço EXISTE.</b> Nenhum formato consegue. A prova de que
 *       um e-mail existe é uma mensagem entregue, e isso é assunto da fatia de alerta.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link EmailInvalidoException}, causa-raiz
 * {@code DADO_INVALIDO}, 400 na borda. A mensagem diz <b>qual</b> regra falhou — "sem
 * arroba" e "domínio sem ponto" pedem correções diferentes. O valor recebido <b>não</b>
 * entra na mensagem: e-mail é dado pessoal, e mensagem de erro acaba em log e em print.</p>
 */
public record Email(String valor) {

    public Email {
        if (valor == null || valor.isBlank()) {
            throw new EmailInvalidoException(valor, "ausente");
        }
        String limpo = valor.strip().toLowerCase();

        int arroba = limpo.indexOf('@');
        if (arroba < 0) {
            throw new EmailInvalidoException(valor, "sem arroba");
        }
        if (arroba != limpo.lastIndexOf('@')) {
            throw new EmailInvalidoException(valor, "mais de uma arroba");
        }
        if (arroba == 0) {
            throw new EmailInvalidoException(valor, "nada antes da arroba");
        }

        String dominio = limpo.substring(arroba + 1);
        if (dominio.isEmpty()) {
            throw new EmailInvalidoException(valor, "nada depois da arroba");
        }
        // Ponto no MEIO do domínio: "ana@com" e "ana@exemplo." não chegam a lugar nenhum.
        int ponto = dominio.indexOf('.');
        if (ponto <= 0 || ponto == dominio.length() - 1) {
            throw new EmailInvalidoException(valor, "dominio sem ponto no meio");
        }
        if (limpo.contains(" ")) {
            throw new EmailInvalidoException(valor, "contem espaco");
        }
        valor = limpo;
    }

    /** O domínio, que é por onde se descobre que cem cadastros vieram do mesmo lugar. */
    public String dominio() {
        return valor.substring(valor.indexOf('@') + 1);
    }

    @Override
    public String toString() {
        return valor;
    }
}
