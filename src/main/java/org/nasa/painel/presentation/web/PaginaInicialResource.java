package org.nasa.painel.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.nasa.core.tempo.Relogio;

import java.time.format.DateTimeFormatter;

/**
 * A página inicial do sistema.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a porta de entrada: diz o que o sistema faz, mostra o
 * <b>relógio da página</b> e oferece a troca de idioma. O relógio não é enfeite — este é
 * um sistema de alerta de desastre, em que "quando" decide se o evento entra na janela de
 * risco; deixar visível <b>em que hora o sistema está pensando</b> evita a confusão mais
 * cara desse domínio, que é alguém ler um horário local e comparar com um dado UTC.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A hora do servidor é UTC, e a página diz isso.</b> O instante vem do
 *       {@link Relogio} injetado — nunca de {@code LocalDateTime.now()}, congelado pela
 *       catraca {@code CatracaRelogioUtcTest}. A hora local do visitante é calculada no
 *       navegador e aparece <b>rotulada</b> ao lado, para que as duas nunca sejam
 *       confundidas.</li>
 *   <li><b>{@code <html lang>} fica no idioma REAL do template</b> ({@code pt-BR}), em
 *       todas as rotas. Trocá-lo ao clicar na bandeira faz o Google concluir que a página
 *       já está traduzida e <b>não traduzir nada</b> — cicatriz registrada na regra de
 *       i18n.</li>
 *   <li><b>O relógio e o seletor de idioma levam {@code translate="no"}</b>: número de
 *       hora traduzido vira lixo, e o seletor traduzido perde o próprio nome.</li>
 * </ol>
 *
 * <p><b>SEM {@code @Transactional}, e por quê.</b> A planta exige a anotação na CLASSE de
 * todo resource de página <i>que toca o banco</i>. Esta não toca: não há consulta, não há
 * entidade. Abrir transação para renderizar texto estático segura conexão do pool sem
 * motivo. <b>Dispensa declarada</b> — e ela cai no instante em que esta página ler o
 * primeiro dado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não há caminho de falha próprio: a página não
 * depende de rede nem de banco. Se o Google Translate estiver fora do ar, a página fica
 * em português e continua funcionando — tradução é conforto, e falha <b>aberta</b> de
 * propósito.</p>
 */
@Path("/")
public class PaginaInicialResource {

    /** Formato do relógio do servidor: ISO-8601 UTC, sem ambiguidade de fuso. */
    private static final DateTimeFormatter FORMATO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneOffset.UTC);

    /**
     * O template da fatia. {@code @Location} é obrigatório porque a árvore canônica põe
     * o arquivo em {@code templates/<fatia>/}, espelhando o pacote Java — sem ele o Qute
     * procuraria {@code templates/index.html} na raiz.
     */
    @Inject
    @Location("painel/index.html")
    Template index;

    @Inject
    Relogio relogio;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        var agora = relogio.agora();
        return index
                .data("horaServidorUtc", FORMATO_UTC.format(agora))
                .data("instanteIso", agora.toString())
                .data("versaoAssets", VersaoDosAssets.ATUAL);
    }
}
