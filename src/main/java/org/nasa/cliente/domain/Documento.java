package org.nasa.cliente.domain;

import org.nasa.cliente.domain.exceptions.DocumentoInvalidoException;

/**
 * O documento que identifica um cliente — normalizado, para que a unicidade funcione.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a chave pela qual uma pessoa é reconhecida. Se o
 * mesmo CPF puder entrar em duas formas diferentes, a unicidade do banco não pega e a
 * mesma pessoa vira dois cadastros — cada um com endereços diferentes, e o alerta indo
 * para metade deles.</p>
 *
 * <p><b>A CORREÇÃO EM RELAÇÃO AO LEGADO.</b> Lá o documento era texto livre
 * ({@code VARCHAR2(18)}) guardado como digitado. {@code "111.222.333-44"} e
 * {@code "11122233344"} eram <b>duas pessoas</b>. Aqui só os dígitos são guardados, então
 * as duas formas colidem no {@code UNIQUE} — que é exatamente o que se quer.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li>Guarda <b>apenas dígitos</b>; pontuação é ruído de digitação.</li>
 *   <li>Aceita 11 dígitos (CPF) ou 14 (CNPJ). Outro tamanho não é documento brasileiro.</li>
 *   <li><b>Não valida dígito verificador</b>, e isto é decisão declarada: trabalho
 *       acadêmico usa documento fictício o tempo todo, e reprovar o dado de teste
 *       inviabilizaria o uso do sistema. A invariante que importa aqui é <b>unicidade e
 *       normalização</b>, não autenticidade — o sistema avisa sobre desastre, não emite
 *       nota fiscal.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link DocumentoInvalidoException} com o
 * valor recusado na mensagem. Nunca normaliza para vazio em silêncio: documento vazio
 * viraria uma "pessoa" que colide com a próxima.</p>
 */
public record Documento(String digitos) {

    public Documento {
        if (digitos == null) {
            throw new DocumentoInvalidoException("(nulo)", "documento ausente");
        }
        String so = digitos.replaceAll("\\D", "");
        if (so.length() != 11 && so.length() != 14) {
            throw new DocumentoInvalidoException(digitos,
                    "esperado 11 digitos (CPF) ou 14 (CNPJ), recebi " + so.length());
        }
        digitos = so;
    }

    /** Como a pessoa costuma ver: {@code 111.222.333-44}. Só para exibição. */
    public String formatado() {
        if (digitos.length() == 11) {
            return digitos.substring(0, 3) + "." + digitos.substring(3, 6) + "."
                    + digitos.substring(6, 9) + "-" + digitos.substring(9);
        }
        return digitos.substring(0, 2) + "." + digitos.substring(2, 5) + "."
                + digitos.substring(5, 8) + "/" + digitos.substring(8, 12) + "-" + digitos.substring(12);
    }

    @Override
    public String toString() {
        return digitos;
    }
}
