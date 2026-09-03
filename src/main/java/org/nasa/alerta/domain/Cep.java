package org.nasa.alerta.domain;

import org.nasa.alerta.domain.exceptions.CepInvalidoException;

/**
 * O CEP, normalizado — a chave pela qual um endereço é encontrado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que a pessoa digita para o sistema descobrir onde
 * ela mora. Como vira parâmetro de consulta a serviços externos e chave de cache, precisa
 * ter uma forma só: {@code "01310-200"}, {@code "01310200"} e {@code "01310 200"} são o
 * mesmo lugar, e tratá-los como três coisas diferentes triplica as chamadas externas —
 * num provedor que aceita <b>uma requisição por segundo</b>.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li>Guarda <b>exatamente 8 dígitos</b>; pontuação é ruído de digitação.</li>
 *   <li>Recusa qualquer outro tamanho: CEP com 7 dígitos não existe, e consultá-lo
 *       gastaria uma chamada externa para receber "não encontrado".</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link CepInvalidoException} com o valor
 * recebido — falha fechada e barata, antes de qualquer rede.</p>
 */
public record Cep(String digitos) {

    public Cep {
        if (digitos == null) {
            throw new CepInvalidoException("(nulo)", "CEP ausente");
        }
        String so = digitos.replaceAll("[^0-9]", "");
        if (so.length() != 8) {
            throw new CepInvalidoException(digitos,
                    "CEP tem 8 digitos, recebi " + so.length());
        }
        digitos = so;
    }

    /** Como a pessoa escreve: {@code 01310-200}. Só para exibição. */
    public String formatado() {
        return digitos.substring(0, 5) + "-" + digitos.substring(5);
    }

    @Override
    public String toString() {
        return digitos;
    }
}
