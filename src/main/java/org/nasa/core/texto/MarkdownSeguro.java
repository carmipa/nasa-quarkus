package org.nasa.core.texto;

import jakarta.enterprise.context.ApplicationScoped;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.text.Normalizer;
import java.util.List;

/**
 * Converte Markdown em HTML — <b>no servidor</b>, e com o HTML embutido escapado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que permite escrever a documentação do sistema em
 * arquivos de texto legíveis, versionados junto com o código, em vez de em HTML à mão que
 * ninguém revisa.</p>
 *
 * <p><b>POR QUE NO SERVIDOR, E NÃO NO NAVEGADOR.</b> A escolha comum é mandar o Markdown
 * cru para a página e converter com uma biblioteca de JavaScript. Isso põe a
 * interpretação de texto do lado de quem lê — e um documento contendo
 * {@code <script>} passa a ser <b>script executando na página</b>, não texto sobre
 * script. Aqui a conversão acontece antes, e o {@code escapeHtml(true)} garante que
 * qualquer marcação embutida no Markdown apareça como <b>texto</b>.</p>
 *
 * <p><b>POR QUE NÃO HÁ ALLOWLIST DE TAGS.</b> A defesa habitual é um sanitizador com uma
 * lista de tags permitidas, que precisa ser mantida à medida que aparecem vetores novos.
 * Aqui não existe essa lista, e é de propósito: com {@code escapeHtml(true)}, o HTML final
 * contém <b>apenas</b> as tags que o próprio renderizador emite. Não há caminho por onde
 * uma tag do documento chegue à página, então não há lista a manter nem sanitizador a
 * atualizar.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@code escapeHtml(true)}</b> — {@code <script>}, {@code <iframe>} e
 *       {@code onerror=} viram texto visível, nunca marcação ativa.</li>
 *   <li><b>{@code sanitizeUrls(true)}</b> — um link {@code javascript:} no Markdown não
 *       vira link executável. É o vetor que sobra quando só se escapa tag.</li>
 *   <li><b>Tabelas ligadas</b>, porque documentação técnica vive delas.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Markdown malformado não falha: o CommonMark
 * é tolerante por especificação e trata o que não entende como texto. Entrada nula vira
 * string vazia — nunca a palavra "null" na página.</p>
 */
@ApplicationScoped
public class MarkdownSeguro {

    private final Parser leitor;
    private final HtmlRenderer escritor;

    public MarkdownSeguro() {
        List<org.commonmark.Extension> extensoes = List.of(TablesExtension.create());
        this.leitor = Parser.builder().extensions(extensoes).build();
        this.escritor = HtmlRenderer.builder()
                .extensions(extensoes)
                // As duas linhas abaixo sao a seguranca inteira desta classe.
                .escapeHtml(true)
                .sanitizeUrls(true)
                .build();
    }

    /** O HTML pronto para a página, com toda marcação embutida escapada. */
    public String paraHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node documento = leitor.parse(markdown);
        return escritor.render(documento);
    }

    /**
     * Um identificador de âncora a partir de um título.
     *
     * <p>Tira acento, baixa a caixa e troca o resto por hífen. Serve para o índice lateral
     * apontar para as seções sem depender de o autor escrever a âncora à mão — o que
     * garante que o índice e o texto nunca discordem.</p>
     */
    public static String ancora(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
