package org.nasa.core.presentation.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.util.Map;
import java.util.Set;

/**
 * O catálogo de ícones do sistema — SVG embutido, servido do próprio servidor.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Ícone não é enfeite numa tela de emergência: numa lista
 * de vinte desastres, a diferença entre um vulcão e uma enchente precisa ser percebida
 * <b>antes</b> de a pessoa ler a palavra. É o mesmo motivo pelo qual placa de trânsito tem
 * desenho e não só texto.</p>
 *
 * <p><b>POR QUE UM CATÁLOGO EM CÓDIGO, E NÃO UMA FONTE DE ÍCONES.</b> As três alternativas
 * comuns foram descartadas, cada uma por um motivo próprio:</p>
 * <ol>
 *   <li><b>Fonte de ícones por CDN</b> (Font Awesome e afins) — o projeto não fala com CDN.
 *       É a mesma decisão do HTMX e do Leaflet, que estão vendorizados: uma tela de alerta
 *       que depende de um servidor de terceiro estar no ar para desenhar o ícone de perigo
 *       tem uma dependência externa <b>no pior momento possível</b>.</li>
 *   <li><b>Emoji</b> — muda de desenho por sistema operacional, e alguns viram um retângulo
 *       vazio. Um ícone de aviso que aparece como caixinha não avisa nada.</li>
 *   <li><b>Arquivos {@code .svg} soltos</b> — cada ícone vira uma requisição, e um ícone
 *       renomeado some da tela sem erro nenhum, exatamente como o documento que sumia do
 *       índice da documentação.</li>
 * </ol>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Ícone desconhecido NÃO é silêncio.</b> Devolve um triângulo de aviso visível,
 *       não string vazia. Um {@code {'mapaa'.icone.raw}} com erro de digitação que
 *       renderiza nada é indistinguível de um layout correto — e ninguém conta os ícones
 *       de uma tela.</li>
 *   <li><b>O SVG é constante de código, nunca entrada de usuário.</b> Ele vai para a página
 *       como HTML cru (pelo {@code .raw} do Qute, obrigatório para SVG funcionar), e cru é
 *       exatamente o que não se pode fazer com texto de fora. Nada aqui vem de fora: o
 *       retorno é montado só de constantes desta classe, e o nome recebido <b>não é
 *       interpolado em lugar nenhum</b> — ele só serve de chave de mapa.</li>
 *   <li><b>Todo ícone tem {@code aria-hidden="true"}</b> e acompanha texto. Ícone sozinho é
 *       adivinhação para quem enxerga e silêncio para quem usa leitor de tela.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Nome desconhecido, nulo ou em branco devolve o
 * ícone de aviso — visível, e portanto corrigível. Nenhum caminho devolve vazio.</p>
 */
@Named("icones")
@ApplicationScoped
public class Icones {

    /**
     * O corpo de cada ícone: só o miolo do SVG, sem o elemento {@code <svg>}.
     *
     * <p>Desenho de traço, 24x24, contorno — a mesma família visual em todos, porque ícone
     * de estilos misturados na mesma tela parece erro de montagem.</p>
     */
    private static final Map<String, String> CORPOS = Map.ofEntries(
            // ---------------------------------------------------------- navegação
            Map.entry("casa", "<path d='M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'/>"
                    + "<path d='M9 22V12h6v10'/>"),
            Map.entry("clientes", "<path d='M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2'/>"
                    + "<circle cx='9' cy='7' r='4'/>"
                    + "<path d='M23 21v-2a4 4 0 0 0-3-3.87'/>"
                    + "<path d='M16 3.13a4 4 0 0 1 0 7.75'/>"),
            Map.entry("pessoa", "<path d='M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2'/>"
                    + "<circle cx='12' cy='7' r='4'/>"),
            Map.entry("contatos", "<path d='M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07"
                    + " 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2"
                    + " 1.72c.13.94.36 1.86.7 2.73a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.35"
                    + "-1.27a2 2 0 0 1 2.11-.45c.87.34 1.79.57 2.73.7A2 2 0 0 1 22 16.92z'/>"),
            Map.entry("enderecos", "<path d='M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z'/>"
                    + "<circle cx='12' cy='10' r='3'/>"),
            Map.entry("desastres", "<path d='M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0"
                    + " 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'/>"
                    + "<line x1='12' y1='9' x2='12' y2='13'/>"
                    + "<line x1='12' y1='17' x2='12.01' y2='17'/>"),
            Map.entry("alertas", "<path d='M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9'/>"
                    + "<path d='M13.73 21a2 2 0 0 1-3.46 0'/>"),
            Map.entry("documentacao", "<path d='M4 19.5A2.5 2.5 0 0 1 6.5 17H20'/>"
                    + "<path d='M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'/>"),
            Map.entry("envelope", "<rect x='2' y='4' width='20' height='16' rx='2'/>"
                    + "<path d='m22 7-10 6L2 7'/>"),

            // ------------------------------------------------------------- ações
            Map.entry("buscar", "<circle cx='11' cy='11' r='8'/>"
                    + "<line x1='21' y1='21' x2='16.65' y2='16.65'/>"),
            Map.entry("adicionar", "<line x1='12' y1='5' x2='12' y2='19'/>"
                    + "<line x1='5' y1='12' x2='19' y2='12'/>"),
            Map.entry("editar", "<path d='M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7'/>"
                    + "<path d='M18.5 2.5a2.12 2.12 0 0 1 3 3L12 15l-4 1 1-4z'/>"),
            Map.entry("excluir", "<line x1='3' y1='6' x2='21' y2='6'/>"
                    + "<path d='M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6'/>"
                    + "<path d='M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2'/>"),
            Map.entry("salvar", "<path d='M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0"
                    + " 1-2 2z'/><polyline points='17 21 17 13 7 13 7 21'/>"
                    + "<polyline points='7 3 7 8 15 8'/>"),
            Map.entry("voltar", "<line x1='19' y1='12' x2='5' y2='12'/>"
                    + "<polyline points='12 19 5 12 12 5'/>"),
            Map.entry("sincronizar", "<path d='M23 4v6h-6'/><path d='M1 20v-6h6'/>"
                    + "<path d='M3.51 9a9 9 0 0 1 14.85-3.36L23 10'/>"
                    + "<path d='M1 14l4.64 4.36A9 9 0 0 0 20.49 15'/>"),
            Map.entry("filtrar", "<polygon points='22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3'/>"),
            Map.entry("enviar", "<line x1='22' y1='2' x2='11' y2='13'/>"
                    + "<polygon points='22 2 15 22 11 13 2 9 22 2'/>"),
            Map.entry("ver", "<path d='M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z'/>"
                    + "<circle cx='12' cy='12' r='3'/>"),
            Map.entry("link-externo", "<path d='M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1"
                    + " 2-2h6'/><polyline points='15 3 21 3 21 9'/>"
                    + "<line x1='10' y1='14' x2='21' y2='3'/>"),

            // -------------------------------------------------------------- dados
            Map.entry("mapa", "<polygon points='1 6 1 22 8 18 16 22 23 18 23 2 16 6 8 2 1 6'/>"
                    + "<line x1='8' y1='2' x2='8' y2='18'/>"
                    + "<line x1='16' y1='6' x2='16' y2='22'/>"),
            Map.entry("satelite", "<polygon points='12 2 2 7 12 12 22 7 12 2'/>"
                    + "<polyline points='2 17 12 22 22 17'/>"
                    + "<polyline points='2 12 12 17 22 12'/>"),
            Map.entry("grafico", "<line x1='12' y1='20' x2='12' y2='10'/>"
                    + "<line x1='18' y1='20' x2='18' y2='4'/>"
                    + "<line x1='6' y1='20' x2='6' y2='16'/>"),
            Map.entry("historico", "<path d='M3 3v5h5'/>"
                    + "<path d='M3.05 13A9 9 0 1 0 6 5.3L3 8'/><path d='M12 7v5l4 2'/>"),
            Map.entry("calendario", "<rect x='3' y='4' width='18' height='18' rx='2'/>"
                    + "<line x1='16' y1='2' x2='16' y2='6'/><line x1='8' y1='2' x2='8' y2='6'/>"
                    + "<line x1='3' y1='10' x2='21' y2='10'/>"),
            Map.entry("relogio", "<circle cx='12' cy='12' r='10'/>"
                    + "<polyline points='12 6 12 12 16 14'/>"),
            Map.entry("globo", "<circle cx='12' cy='12' r='10'/>"
                    + "<line x1='2' y1='12' x2='22' y2='12'/>"
                    + "<path d='M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10"
                    + " 15.3 15.3 0 0 1 4-10z'/>"),
            Map.entry("banco", "<ellipse cx='12' cy='5' rx='9' ry='3'/>"
                    + "<path d='M21 12c0 1.66-4 3-9 3s-9-1.34-9-3'/>"
                    + "<path d='M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5'/>"),
            Map.entry("atividade", "<polyline points='22 12 18 12 15 21 9 3 6 12 2 12'/>"),
            Map.entry("noticias", "<path d='M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16"
                    + "a2 2 0 0 1-4 0V9h4'/><path d='M18 14h-8'/><path d='M15 18h-5'/>"
                    + "<path d='M10 6h8v4h-8z'/>"),
            Map.entry("camadas", "<path d='M12 2 2 7l10 5 10-5-10-5z'/>"
                    + "<path d='m2 17 10 5 10-5'/><path d='m2 12 10 5 10-5'/>"),

            // ------------------------------------------------------------- estado
            Map.entry("ok", "<polyline points='20 6 9 17 4 12'/>"),
            Map.entry("erro", "<circle cx='12' cy='12' r='10'/>"
                    + "<line x1='15' y1='9' x2='9' y2='15'/><line x1='9' y1='9' x2='15' y2='15'/>"),
            Map.entry("aviso", "<circle cx='12' cy='12' r='10'/>"
                    + "<line x1='12' y1='8' x2='12' y2='12'/>"
                    + "<line x1='12' y1='16' x2='12.01' y2='16'/>"),
            Map.entry("info", "<circle cx='12' cy='12' r='10'/>"
                    + "<line x1='12' y1='16' x2='12' y2='12'/>"
                    + "<line x1='12' y1='8' x2='12.01' y2='8'/>"),
            Map.entry("escudo", "<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'/>"),
            Map.entry("nuvem", "<path d='M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z'/>"),
            Map.entry("fogo", "<path d='M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143"
                    + "-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153"
                    + ".433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z'/>"),
            Map.entry("agua", "<path d='M12 2.69l5.66 5.66a8 8 0 1 1-11.31 0z'/>"));

    /** O que se mostra quando o nome não existe — visível, e portanto corrigível. */
    private static final String DESCONHECIDO =
            "<path d='M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2"
                    + " 0 0 0-3.42 0z'/><line x1='12' y1='9' x2='12' y2='13'/>"
                    + "<line x1='12' y1='17' x2='12.01' y2='17'/>";

    /**
     * O SVG completo de um ícone, pronto para a página.
     *
     * <p><b>Precisa de {@code .raw} no template</b> — é SVG, e o Qute escaparia as tags.
     * É seguro porque nada aqui vem de fora, conforme a invariante 2.</p>
     *
     * @param nome a chave do catálogo
     * @return o elemento {@code <svg>} inteiro; nunca vazio, nunca nulo
     */
    public static String svg(String nome) {
        String corpo = (nome == null || nome.isBlank())
                ? DESCONHECIDO
                : CORPOS.getOrDefault(nome, DESCONHECIDO);
        // `currentColor` faz o icone herdar a cor do texto ao redor: um icone dentro de um
        // botao vermelho fica vermelho sem CSS novo, e um dentro de link fica da cor do link.
        // `width/height: 1em` faz ele acompanhar o TAMANHO da fonte pela mesma razao —
        // e atende a regra de porcentagem, porque `em` escala e `px` nao.
        return "<svg class='icone' viewBox='0 0 24 24' width='1em' height='1em' fill='none'"
                + " stroke='currentColor' stroke-width='2' stroke-linecap='round'"
                + " stroke-linejoin='round' aria-hidden='true' focusable='false'>"
                + corpo + "</svg>";
    }

    /** Se o nome existe — a guarda usa isto para provar que nenhuma tela pede ícone torto. */
    public static boolean existe(String nome) {
        return nome != null && CORPOS.containsKey(nome);
    }

    /** Todos os nomes, para a guarda e para a documentação. */
    public static Set<String> nomes() {
        return CORPOS.keySet();
    }

    /**
     * O que a tag {@code {#icone 'mapa' /}} chama.
     *
     * <p><b>POR QUE UM BEAN CDI, E NÃO UMA {@code @TemplateExtension}.</b> Medido em
     * 02/09/2026: a extensão foi escrita como {@code {'historico'.icone.raw}} e o Qute
     * <b>não reconheceu aquilo como expressão</b> — expressão não pode começar por aspas.
     * Ele então imprimiu {@code {'historico'.icone.raw}} como texto literal na página, com
     * status 200 e sem erro nenhum.</p>
     *
     * <p>Esse é o motivo de a verificação ter sido feita medindo o HTML em vez de conferir
     * o status: <b>200 não prova que a página está certa</b>, e aqui o 200 vinha com o
     * código-fonte do template à mostra para o visitante.</p>
     */
    public String de(String nome) {
        return svg(nome);
    }
}
