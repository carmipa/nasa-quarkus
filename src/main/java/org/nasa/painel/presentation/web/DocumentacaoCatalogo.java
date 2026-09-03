package org.nasa.painel.presentation.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
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
@Named("catalogoDocs")
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
    public record Secao(String slug, String titulo, String resumo, String icone,
                        int matiz) {

        // SEM VALIDACAO EM TEMPO DE EXECUCAO, e de proposito.
        //
        // A primeira versao lancava `IllegalArgumentException` para slug em branco, e a
        // CATRACA DE EXCECAO ESPECIFICA reprovou — corretamente: falha sem nome proprio e
        // falha sem causa-raiz. A saida obvia seria criar uma excecao nomeada; a saida
        // certa e nao lancar nada.
        //
        // Este catalogo e feito de LITERAIS no proprio arquivo. Nao ha entrada de usuario,
        // nem leitura de arquivo, nem desserializacao: um slug em branco so pode aparecer
        // se alguem editar a linha logo abaixo. Isso e erro de compilacao-tempo-de-escrita,
        // e o instrumento certo para ele e o TESTE, que reprova a build — nao uma excecao
        // que so apareceria com o sistema no ar, para um visitante, numa tela de erro.
        //
        // Quem guarda: `CatalogoDaDocumentacaoTest` — slug em branco, icone inexistente,
        // secao orfa, slug repetido e secao vazia, cada um com seu caso.
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
                      String descricao, String icone) {
    }

    /** As seções, na ordem em que fazem sentido para quem chega agora. */
    public static final List<Secao> SECOES = List.of(
            new Secao("fundamentos", "Fundamentos",
                    "O que o sistema faz, como está organizado, e por quê",
                    "info", 210),
            new Secao("fatias", "As fatias",
                    "Cada recorte vertical do domínio, com as invariantes que ele protege",
                    "camadas", 265),
            new Secao("infraestrutura", "Infraestrutura",
                    "Banco, migrações, guardas e o que roda no arranque",
                    "banco", 160),
            new Secao("decisoes", "Decisões e cicatrizes",
                    "O que foi medido, o que mudou, e o preço de cada engano",
                    "historico", 35),
            new Secao("referencia", "Referência",
                    "API, fontes de dados e como rodar",
                    "documentacao", 300));

    /** Os documentos, na ordem de leitura. */
    public static final List<Doc> DOCUMENTOS = List.of(
            new Doc("visao-geral", "00-visao-geral", "fundamentos",
                    "Visão geral",
                    "O problema, a resposta e o caminho de um alerta do começo ao fim",
                    "globo"),
            new Doc("arquitetura", "01-arquitetura", "fundamentos",
                    "Arquitetura",
                    "Fatias verticais, peers e kernel — e a guarda que impede o acoplamento",
                    "camadas"),
            new Doc("stack", "02-stack", "fundamentos",
                    "A pilha e o porquê",
                    "Java 25, Quarkus, Qute e HTMX — e o que saiu do projeto original",
                    "atividade"),

            new Doc("fatia-evento", "13-fatia-evento", "fatias",
                    "Evento",
                    "O que a NASA publica, e o defeito de 456 km",
                    "desastres"),
            new Doc("fatia-alerta", "14-fatia-alerta", "fatias",
                    "Alerta",
                    "A saída do sistema — montada na hora, e sem gravar nada",
                    "alertas"),

            new Doc("banco", "20-banco-e-migracoes", "infraestrutura",
                    "Banco e migrações",
                    "SQLite, o aplicador próprio e o que cada migração mudou",
                    "banco"),
            new Doc("guardas", "21-guardas", "infraestrutura",
                    "Guardas executáveis",
                    "Segredos, caminhos proibidos, fronteira arquitetural, fuso e geometria",
                    "escudo"),
            new Doc("seguranca", "22-seguranca", "infraestrutura",
                    "Segurança",
                    "SSRF, XXE, credenciais, mascaramento e o que cada trava impede",
                    "escudo"),

            // Esta era a fatia `inscrito`, e a fatia NAO EXISTE MAIS. O documento
            // descrevia como se cadastrar num sistema que deixou de guardar gente — e
            // documentacao de vitrine que descreve tela inexistente e pior que a falta
            // dela, porque quem le acredita. Virou o que sempre foi de fato: a DECISAO
            // de nao guardar ninguem, com o motivo.
            new Doc("sem-cadastro", "10-sem-cadastro", "decisoes",
                    "Sem cadastro",
                    "Por que quatro fatias e duas tabelas foram removidas do sistema",
                    "excluir"),
            new Doc("defeitos", "30-defeitos-medidos", "decisoes",
                    "Defeitos medidos",
                    "Os enganos do projeto original, com o número que os torna reais",
                    "aviso"),
            new Doc("postgres-sqlite", "31-de-postgres-a-sqlite", "decisoes",
                    "De PostgreSQL a SQLite",
                    "As duas trocas medidas, o que se perdeu, e a recomendação que não venceu",
                    "historico"),

            new Doc("api", "40-api", "referencia",
                    "API",
                    "Todos os endpoints, com o que cada um garante",
                    "link-externo"),
            new Doc("fontes", "41-fontes-de-dados", "referencia",
                    "Fontes de dados",
                    "NASA, BrasilAPI, ViaCEP, Nominatim e GDACS — todas abertas",
                    "nuvem"),
            new Doc("rodar", "42-como-rodar", "referencia",
                    "Como rodar",
                    "Desenvolvimento, testes e produção",
                    "sincronizar"),
            new Doc("interface", "43-interface", "referencia",
                    "Interface",
                    "Ícones, dicas de campo e a regra da porcentagem — e o que a tela recusa fazer",
                    "ver"));

    /**
     * As seções, para o template.
     *
     * <p><b>É um método de instância de propósito, e não o campo estático.</b> O template
     * chega ao bean pelo proxy do CDI, e <b>ler um campo através do proxy devolve o valor
     * vazio</b>, não o configurado — foi assim que um teste deste projeto leu {@code 0} num
     * limite que valia 30 e passou achando que media algo. Método atravessa o proxy.</p>
     */
    public List<Secao> getSecoes() {
        return SECOES;
    }

    /** Os documentos de uma seção, na ordem declarada. */
    public List<Doc> daSecao(String slugDaSecao) {
        return DOCUMENTOS.stream().filter(d -> d.secao().equals(slugDaSecao)).toList();
    }

    /**
     * A seção de um documento — o rótulo da trilha, no topo da página.
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Documento com seção que não existe no
     * catálogo devolve {@link Optional#empty()}, e a trilha simplesmente não aparece.
     * Lançar aqui derrubaria a página inteira por causa de uma migalha de navegação.</p>
     */
    public Optional<Secao> secaoDe(Doc doc) {
        if (doc == null) {
            return Optional.empty();
        }
        return SECOES.stream().filter(x -> x.slug().equals(doc.secao())).findFirst();
    }

    /**
     * Quantos minutos de leitura um documento tem.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO.</b> Quem chega numa lista de catorze documentos decide o
     * que abrir pelo tamanho. O número dá essa informação antes do clique.</p>
     *
     * <p><b>É MEDIDO DO ARQUIVO, e não declarado no catálogo.</b> O binmapper, de onde este
     * padrão veio, escreve o número à mão — e ali ele é uma promessa que envelhece: o
     * documento cresce, o número fica. Contar as palavras na hora não pode ficar
     * desatualizado, e o custo é ler um arquivo que a página já vai ler de todo jeito.</p>
     *
     * <p><b>200 palavras por minuto</b> é a média de leitura de texto técnico em tela. O
     * resultado é arredondado para cima e nunca é zero: "0 min de leitura" ao lado de um
     * texto de meia página parece defeito, e é.</p>
     *
     * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Arquivo ausente ou ilegível devolve
     * {@code 0}, e a página <b>omite</b> o rótulo em vez de mostrar zero — o
     * {@code markdownDe} já registrou o motivo em WARN.</p>
     */
    public int minutosDe(String slug) {
        return markdownDe(slug).map(md -> {
            // `split` em qualquer sequencia de espaco: o Markdown tem tabela, lista e
            // bloco de codigo, e contar caracteres daria numero muito diferente entre um
            // texto corrido e uma tabela do mesmo tamanho aparente.
            int palavras = md.isBlank() ? 0 : md.trim().split("\s+").length;
            return palavras == 0 ? 0 : Math.max(1, (int) Math.ceil(palavras / 200.0));
        }).orElse(0);
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
