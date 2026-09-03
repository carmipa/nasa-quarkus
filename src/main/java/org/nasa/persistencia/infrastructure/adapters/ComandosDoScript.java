package org.nasa.persistencia.infrastructure.adapters;

import java.util.ArrayList;
import java.util.List;

/**
 * Parte um script SQL nos comandos que o compõem.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O driver do SQLite executa <b>um comando por chamada</b>.
 * Mandar o script inteiro não dá erro: ele executa o primeiro e ignora o resto,
 * <b>silenciosamente</b>. Foi exatamente isso que aconteceu em 03/09/2026 — a migração
 * registrou <i>"aplicada"</i> tendo criado <b>1 de 9</b> objetos, e o sistema só falhou
 * depois, na primeira consulta, com "no such table".</p>
 *
 * <p><b>O PIOR TIPO DE FALHA:</b> a migração ficou marcada como aplicada. Um segundo
 * arranque não a repetiria — o banco ficaria permanentemente pela metade, e a evidência
 * disponível (o log dizendo "aplicadas=1") afirmava que estava tudo certo.</p>
 *
 * <p><b>POR QUE ISTO NÃO É UM {@code split(";")}.</b> O código anterior tinha um comentário
 * avisando que dividir por ponto e vírgula é bomba-relógio, e o aviso está correto: um
 * {@code ;} dentro de literal ou de comentário parte um comando ao meio e produz erro de
 * sintaxe em SQL que está certo. Esta classe divide <b>sabendo onde está</b>:</p>
 * <ul>
 *   <li>dentro de {@code '...'} — inclusive o {@code ''} que escapa a própria aspa;</li>
 *   <li>dentro de {@code "..."} — identificador entre aspas duplas;</li>
 *   <li>depois de {@code --} até o fim da linha;</li>
 *   <li>entre {@code /*} e o fechamento do bloco.</li>
 * </ul>
 * <p>Em nenhum desses contextos o {@code ;} termina comando.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Comando vazio nunca é devolvido.</b> Um {@code ;} sobrando no fim do arquivo, ou
 *       duas quebras entre comandos, produziriam uma string em branco — que o driver
 *       recusa com erro de sintaxe num script que está correto.</li>
 *   <li><b>Comentário no fim do arquivo não vira comando.</b> É o caso mais comum de
 *       "comando vazio", e o mais chato de diagnosticar.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Script nulo ou em branco devolve lista vazia —
 * e quem chama trata isso como migração sem conteúdo, que é um erro que vale reportar.
 * Aspas ou comentário sem fechamento não lançam: o resto do texto vira o último comando, e
 * o banco recusa com uma mensagem de sintaxe que diz onde está o problema. Inventar um erro
 * próprio aqui esconderia a mensagem do motor, que é melhor.</p>
 */
public final class ComandosDoScript {

    private ComandosDoScript() {
    }

    /**
     * Os comandos, na ordem em que aparecem.
     *
     * @param script o conteúdo do arquivo de migração
     * @return lista sem comandos vazios; nunca {@code null}
     */
    public static List<String> de(String script) {
        List<String> comandos = new ArrayList<>();
        if (script == null || script.isBlank()) {
            return comandos;
        }

        StringBuilder atual = new StringBuilder();
        boolean emAspaSimples = false;
        boolean emAspaDupla = false;
        boolean emComentarioDeLinha = false;
        boolean emComentarioDeBloco = false;

        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            char proximo = (i + 1 < script.length()) ? script.charAt(i + 1) : '\0';

            if (emComentarioDeLinha) {
                atual.append(c);
                if (c == '\n') {
                    emComentarioDeLinha = false;
                }
                continue;
            }
            if (emComentarioDeBloco) {
                atual.append(c);
                if (c == '*' && proximo == '/') {
                    atual.append(proximo);
                    i++;
                    emComentarioDeBloco = false;
                }
                continue;
            }
            if (emAspaSimples) {
                atual.append(c);
                if (c == '\'') {
                    // `''` dentro de literal e a propria aspa escapada, nao o fim dela.
                    if (proximo == '\'') {
                        atual.append(proximo);
                        i++;
                    } else {
                        emAspaSimples = false;
                    }
                }
                continue;
            }
            if (emAspaDupla) {
                atual.append(c);
                if (c == '"') {
                    if (proximo == '"') {
                        atual.append(proximo);
                        i++;
                    } else {
                        emAspaDupla = false;
                    }
                }
                continue;
            }

            // Fora de tudo: aqui os delimitadores valem.
            if (c == '-' && proximo == '-') {
                emComentarioDeLinha = true;
                atual.append(c);
                continue;
            }
            if (c == '/' && proximo == '*') {
                emComentarioDeBloco = true;
                atual.append(c).append(proximo);
                i++;
                continue;
            }
            if (c == '\'') {
                emAspaSimples = true;
                atual.append(c);
                continue;
            }
            if (c == '"') {
                emAspaDupla = true;
                atual.append(c);
                continue;
            }
            if (c == ';') {
                acrescentar(comandos, atual);
                atual.setLength(0);
                continue;
            }
            atual.append(c);
        }
        // O que sobrou depois do ultimo `;` — script sem ponto e virgula final e valido.
        acrescentar(comandos, atual);
        return comandos;
    }

    /**
     * Acrescenta, se sobrar comando de verdade.
     *
     * <p>Um trecho que só tem comentário e espaço <b>não é comando</b>: mandá-lo ao driver
     * produz erro de sintaxe num script correto, e é o caso mais comum — o comentário no
     * fim do arquivo, depois do último {@code ;}.</p>
     */
    private static void acrescentar(List<String> comandos, StringBuilder trecho) {
        String texto = trecho.toString();
        if (temComandoDeVerdade(texto)) {
            comandos.add(texto.trim());
        }
    }

    /** Se sobra alguma coisa depois de tirar comentário e espaço. */
    static boolean temComandoDeVerdade(String trecho) {
        if (trecho == null || trecho.isBlank()) {
            return false;
        }
        String semBloco = trecho.replaceAll("(?s)/\\*.*?\\*/", " ");
        String semLinha = semBloco.replaceAll("(?m)--[^\\n]*", " ");
        return !semLinha.isBlank();
    }
}
