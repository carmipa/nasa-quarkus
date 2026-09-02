package org.nasa.core.presentation.web;

import io.quarkus.qute.TemplateExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Métodos que os templates podem chamar em qualquer texto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Existe por causa de um defeito real, e a história dele
 * vale mais que o código.</p>
 *
 * <p><b>O DEFEITO, MEDIDO EM 02/09/2026.</b> Eu escrevi {@code {termo.urlEncoded}} na
 * paginação da lista de clientes, supondo que o Qute tivesse essa extensão. <b>Não tem.</b>
 * O erro é {@code Property "urlEncoded" not found on the base object "java.lang.String"} —
 * e ele derruba a renderização com 500.</p>
 *
 * <p><b>POR QUE NÃO APARECEU ANTES:</b> aquela expressão vive <b>dentro</b> do bloco de
 * paginação, que só é renderizado quando existe uma página anterior ou uma próxima. Com
 * quatro clientes na base, o bloco nunca chegou a ser desenhado — e a tela passou nos
 * testes e no uso à mão. O defeito estava lá desde o primeiro dia, esperando o quinto
 * cliente. Só apareceu quando a lista de desastres, com quarenta eventos, paginou de
 * verdade.</p>
 *
 * <p><b>A LIÇÃO.</b> Template não é código compilado: uma expressão errada dentro de um
 * ramo condicional é invisível até aquele ramo acontecer. Por isso a correção virou
 * <b>extensão de verdade</b>, e não um valor pré-codificado passado por cada resource —
 * assim o próximo {@code urlEncoded} que alguém escrever já funciona, em vez de repetir a
 * mesma surpresa noutra tela.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@code null} vira texto vazio</b>, nunca a palavra "null" na URL — que viraria
 *       uma busca literal por {@code null}.</li>
 *   <li><b>Codifica para uso em QUERY STRING</b>, que é onde estes valores vão. O
 *       {@code +} do {@code URLEncoder} representa espaço corretamente ali; em caminho de
 *       URL não representaria, e por isso este método não serve para caminho.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não falha: UTF-8 sempre existe, e entrada
 * nula tem resposta definida.</p>
 */
@TemplateExtension
public final class ExtensoesDeTemplate {

    private ExtensoesDeTemplate() {
    }

    /**
     * O texto pronto para entrar numa query string.
     *
     * <p>Usado como {@code {termo.urlEncoded}} nos templates.</p>
     */
    public static String urlEncoded(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
