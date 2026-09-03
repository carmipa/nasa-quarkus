package org.nasa.core.presentation.web;

import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.core.tempo.Relogio;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Preenche, em um só lugar, tudo que a moldura compartilhada precisa para renderizar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O {@code layout/base.html} desenha cabeçalho, relógio,
 * seletor de idioma, menu e rodapé em <b>toda</b> página, e para isso exige quatro dados.
 * Esta classe os fornece de uma vez só, para que nenhuma tela precise lembrar deles.</p>
 *
 * <p><b>CORREÇÃO DO QUE EU ESCREVI AQUI ANTES</b>, medido em 02/09/2026: a primeira versão
 * desta documentação afirmava que esquecer um dado renderizaria a página vazia com status
 * 200. É <b>falso</b>. O Qute é estrito e derruba a renderização —
 * {@code Key "criado" not found in the template data map}, com 500. A garantia contra o
 * esquecimento já existia no motor de template; esta classe não a inventou.</p>
 *
 * <p><b>O que ela realmente compra</b>, então, é outra coisa e continua valendo: quatro
 * linhas repetidas em treze telas divergem na primeira vez que uma delas mudar, e a
 * divergência não seria um erro — seria uma tela com o relógio formatado diferente das
 * outras doze.</p>
 *
 * <p><b>Por que uma classe, e não copiar quatro linhas em cada resource:</b> são treze
 * telas. Quatro linhas repetidas treze vezes é a garantia de que uma delas vai divergir —
 * e a que divergir não vai avisar. Aqui, esquecer é impossível: ou a página passa pela
 * moldura, ou não compila com o tipo certo.</p>
 *
 * <p><b>Por que mora no {@code core} e não no {@code painel}:</b> a moldura serve a todas
 * as fatias, e <b>fatia não conhece fatia</b>. Se isto ficasse em {@code painel}, a fatia
 * {@code cliente} teria de importar de {@code painel} para desenhar o próprio cabeçalho —
 * e a guarda de fronteira reprovaria o build, corretamente.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A hora do servidor é UTC</b>, com a zona explícita na formatação — nunca a
 *       padrão da JVM. É a mesma disciplina que a {@code CatracaDeFusoUtc} protege no
 *       arranque, aplicada onde o número chega ao olho de quem lê.</li>
 *   <li><b>O instante vem do {@link Relogio} injetado</b>, nunca de
 *       {@code LocalDateTime.now()}. Congelar o relógio em teste é o que torna a página
 *       verificável.</li>
 *   <li><b>A seção ativa é decidida no SERVIDOR.</b> Descobri-la no navegador faria o
 *       menu piscar sem destaque até o JavaScript rodar, e ficar sem destaque nenhum se
 *       ele falhar.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não tem caminho de falha próprio: só lê o
 * relógio e escreve texto. Se o {@code Relogio} falhasse, a exceção sobe para o mapeador
 * de borda como qualquer outra.</p>
 */
@ApplicationScoped
public class MolduraDaPagina {

    /** Formato do relógio do servidor: sem ambiguidade de fuso, por construção. */
    private static final DateTimeFormatter FORMATO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Inject
    Relogio relogio;

    /**
     * Veste a página com a moldura comum.
     *
     * @param pagina      o template já com os dados próprios da tela
     * @param secaoAtiva  qual item do menu destacar: {@code inicio}, {@code clientes},
     *                    {@code desastres} ou {@code contato}
     * @return o mesmo template, agora com tudo que o {@code layout/base.html} exige
     */
    public TemplateInstance vestir(TemplateInstance pagina, String secaoAtiva) {
        return vestir(pagina, secaoAtiva, false);
    }

    /**
     * Como {@link #vestir(TemplateInstance, String)}, dizendo se a página desenha mapa.
     *
     * <p><b>Por que a moldura precisa saber disso.</b> A atribuição do OpenStreetMap é
     * exigida pela licença ODbL <b>onde o dado aparece</b>. Ela estava no rodapé de TODA
     * página — inclusive no cadastro de cliente, que não desenha mapa nenhum. Não é apenas
     * ruído: numa página sem mapa, a linha afirma uma procedência que não existe ali, e
     * enfraquece a atribuição justamente onde ela é obrigatória.</p>
     *
     * <p><b>O padrão é {@code false}</b>, e é o padrão certo: página nova não nasce
     * atribuindo dado que não usa. Quem tem mapa diz que tem.</p>
     */
    public TemplateInstance vestir(TemplateInstance pagina, String secaoAtiva, boolean temMapa) {
        var agora = relogio.agora();
        return pagina
                .data("horaServidorUtc", FORMATO_UTC.format(agora))
                .data("instanteIso", agora.toString())
                .data("versaoAssets", VersaoDosAssets.ATUAL)
                .data("secaoAtiva", secaoAtiva)
                // Fornecida SEMPRE, nos dois caminhos: o Qute e estrito, e chave ausente
                // e 500 — nao campo vazio. Ja aconteceu tres vezes neste projeto.
                .data("temMapa", temMapa);
    }
}
