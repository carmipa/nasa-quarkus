package org.nasa.painel.presentation.web;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * O índice da documentação — quais documentos existem, e em que seção.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A documentação mora em arquivos Markdown, versionados
 * junto com o código. Este catálogo é o que dá <b>ordem e agrupamento</b> a eles: sem ele,
 * a página seria uma lista de arquivos em ordem alfabética, e o leitor teria de descobrir
 * sozinho por onde começar.</p>
 *
 * <p><b>POR QUE O ÍNDICE É DECLARADO EM CÓDIGO, e não deduzido da pasta.</b> Varredura de
 * diretório muda de resultado entre a IDE e o jar, e a ordem passaria a depender do
 * empacotamento. Pior: um documento novo apareceria na página <b>sem seção</b>, no fim da
 * lista, e ninguém perceberia. Declarado, o arquivo esquecido aqui simplesmente não
 * aparece — e o que está aqui e não existe no disco é <b>reportado</b>, em vez de sumir em
 * silêncio.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Travessia de caminho é impossível.</b> Todo caminho é resolvido contra a pasta
 *       base e verificado com {@code startsWith} <b>depois</b> de normalizado — sem isso,
 *       um slug com {@code ../} leria arquivos de qualquer lugar do disco. É a mesma
 *       família de defeito do proxy de imagens, e a mesma disciplina.</li>
 *   <li><b>Documento declarado e ausente é RELATADO</b>, não escondido. Uma página de
 *       documentação com um item a menos é indistinguível de uma correta.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Arquivo ilegível vira {@link Optional#empty()}
 * e um registro em WARN. A página continua inteira, com os demais documentos — perder a
 * documentação toda por causa de um arquivo seria a troca errada.</p>
 */
@ApplicationScoped
public class DocumentacaoCatalogo {

    private static final Logger LOG = Logger.getLogger(DocumentacaoCatalogo.class);
    private static final String OPERACAO = "ler-documentacao";

    @ConfigProperty(name = "nasa.docs.pasta", defaultValue = "docs/documentacao")
    String pastaDocs;

    /**
     * Uma seção do índice.
     *
     * @param slug   identificador na URL
     * @param titulo como aparece no índice
     * @param resumo uma linha dizendo o que se encontra ali
     */
    public record Secao(String slug, String titulo, String resumo) {
    }

    /**
     * Um documento.
     *
     * @param slug      identificador na URL — nunca vem de entrada do usuário
     * @param arquivo   nome do {@code .md} na pasta, sem extensão
     * @param secao     a que seção pertence
     * @param titulo    como aparece no índice
     * @param descricao uma linha sobre o conteúdo
     */
    public record Doc(String slug, String arquivo, String secao, String titulo,
                      String descricao) {
    }

    /** As seções, na ordem em que fazem sentido para quem chega agora. */
    public static final List<Secao> SECOES = List.of(
            new Secao("fundamentos", "Fundamentos",
                    "O que o sistema faz, como está organizado, e por quê"),
            new Secao("fatias", "As fatias",
                    "Cada recorte vertical do domínio, com as invariantes que ele protege"),
            new Secao("infraestrutura", "Infraestrutura",
                    "Banco, migrações, guardas e o que roda no arranque"),
            new Secao("decisoes", "Decisões e cicatrizes",
                    "O que foi medido, o que mudou, e o preço de cada engano"),
            new Secao("referencia", "Referência",
                    "API, fontes de dados e como rodar"));

    /** Os documentos, na ordem de leitura. */
    public static final List<Doc> DOCUMENTOS = List.of(
            new Doc("visao-geral", "00-visao-geral", "fundamentos",
                    "Visão geral",
                    "O problema, a resposta e o caminho de um alerta do começo ao fim"),
            new Doc("arquitetura", "01-arquitetura", "fundamentos",
                    "Arquitetura",
                    "Fatias verticais, peers e kernel — e a guarda que impede o acoplamento"),
            new Doc("stack", "02-stack", "fundamentos",
                    "A pilha e o porquê",
                    "Java 25, Quarkus, Qute e HTMX — e o que saiu do projeto original"),

            new Doc("fatia-inscrito", "10-fatia-inscrito", "fatias",
                    "Inscrito",
                    "Quem pediu para ser avisado — e o que substituiu três fatias"),
            new Doc("fatia-evento", "13-fatia-evento", "fatias",
                    "Evento", "O que a NASA publica, e o defeito de 456 km"),
            new Doc("fatia-alerta", "14-fatia-alerta", "fatias",
                    "Alerta", "A saída do sistema, em padrão outbox"),

            new Doc("banco", "20-banco-e-migracoes", "infraestrutura",
                    "Banco e migrações",
                    "PostgreSQL, checksum imutável e o que cada migração mudou"),
            new Doc("guardas", "21-guardas", "infraestrutura",
                    "Guardas executáveis",
                    "Segredos, caminhos proibidos, fronteira arquitetural e fuso"),
            new Doc("seguranca", "22-seguranca", "infraestrutura",
                    "Segurança",
                    "SSRF, XXE, credenciais, mascaramento e o que cada trava impede"),

            new Doc("defeitos", "30-defeitos-medidos", "decisoes",
                    "Defeitos medidos",
                    "Os enganos do projeto original, com o número que os torna reais"),
            new Doc("postgres-sqlite", "31-de-postgres-a-sqlite", "decisoes",
                    "De PostgreSQL a SQLite",
                    "As duas trocas medidas, o que se perdeu, e a recomendação que não venceu"),

            new Doc("api", "40-api", "referencia",
                    "API", "Todos os endpoints, com o que cada um garante"),
            new Doc("fontes", "41-fontes-de-dados", "referencia",
                    "Fontes de dados",
                    "NASA, BrasilAPI, ViaCEP, Nominatim e GDACS — todas abertas"),
            new Doc("rodar", "42-como-rodar", "referencia",
                    "Como rodar", "Desenvolvimento, testes e produção"),
            new Doc("interface", "43-interface", "referencia",
                    "Interface",
                    "Ícones, dicas de campo e a regra da porcentagem — e o que a tela recusa fazer"));

    /** Os documentos de uma seção, na ordem declarada. */
    public List<Doc> daSecao(String slugDaSecao) {
        return DOCUMENTOS.stream().filter(d -> d.secao().equals(slugDaSecao)).toList();
    }

    public Optional<Doc> porSlug(String slug) {
        return DOCUMENTOS.stream().filter(d -> d.slug().equals(slug)).findFirst();
    }

    /** O índice inteiro, agrupado — é o que a página lateral desenha. */
    public Map<Secao, List<Doc>> indice() {
        Map<Secao, List<Doc>> mapa = new LinkedHashMap<>();
        for (Secao s : SECOES) {
            mapa.put(s, daSecao(s.slug()));
        }
        return mapa;
    }

    /**
     * O Markdown cru de um documento.
     *
     * <p><b>A trava de travessia de caminho é a razão de este método existir</b> em vez de
     * uma leitura direta. O nome do arquivo vem do catálogo, não da URL — mas isso é uma
     * garantia de <i>hoje</i>. Resolver contra a base e conferir {@code startsWith} depois
     * de {@code normalize()} torna a travessia impossível mesmo que amanhã alguém passe a
     * aceitar o nome de fora.</p>
     */
    public Optional<String> markdownDe(String slug) {
        Optional<Doc> doc = porSlug(slug);
        if (doc.isEmpty()) {
            return Optional.empty();
        }
        Path base = base();
        Path caminho = base.resolve(doc.get().arquivo() + ".md").normalize();

        // A verificacao vem DEPOIS do normalize: e o normalize que resolve os `..`.
        if (!caminho.startsWith(base) || !Files.isRegularFile(caminho)) {
            // Documento declarado e ausente e RELATADO. Uma pagina de documentacao com um
            // item a menos e indistinguivel de uma correta.
            LOG.warn(Registro.recusa(OPERACAO, doc.get().arquivo(), "ARQUIVO_AUSENTE"));
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(caminho));
        } catch (IOException naoLeu) {
            LOG.warn(Registro.recusa(OPERACAO, doc.get().arquivo(), "NAO_LEU"), naoLeu);
            return Optional.empty();
        }
    }

    /** Quais documentos declarados NÃO existem no disco — a guarda usa isto. */
    public List<String> declaradosSemArquivo() {
        return DOCUMENTOS.stream()
                .filter(d -> markdownDe(d.slug()).isEmpty())
                .map(Doc::arquivo)
                .toList();
    }

    Path base() {
        return Path.of(pastaDocs).toAbsolutePath().normalize();
    }
}
