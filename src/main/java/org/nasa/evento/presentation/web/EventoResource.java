package org.nasa.evento.presentation.web;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.evento.application.ConsultarEventosUseCase;
import org.nasa.evento.application.EventosProximosUseCase;
import org.nasa.evento.application.SincronizarEventosUseCase;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;
import org.nasa.geo.domain.Coordenada;

import java.util.List;

/**
 * A API de eventos naturais.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Expõe o que a NASA publica e a consulta que dispara o
 * alerta. A EONET é <b>aberta</b> — nenhum endpoint aqui carrega chave de API.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Sincronizar é POST, nunca GET.</b> É uma operação que ESCREVE: um GET que
 *       escreve é executado por rastreador, por pré-carregamento de navegador e por
 *       qualquer coisa que resolva "visitar" o link — sem ninguém clicar.</li>
 *   <li><b>A resposta de proximidade traz a DISTÂNCIA.</b> Sem ela, a tela recalcularia —
 *       e um segundo cálculo é um segundo lugar para divergir.</li>
 *   <li><b>Cada evento declara se participa do alerta</b>, com o motivo quando não
 *       participa. Evento sem coordenada é comum e legítimo, e invisível de outra forma.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> NASA fora ⇒ 503 e a base local intacta.
 * Contrato mudado ⇒ 502, que manda olhar o formato e não a rede.</p>
 */
@Path("/api/eventos")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class EventoResource {

    @Inject
    SincronizarEventosUseCase sincronizar;

    @Inject
    ConsultarEventosUseCase consultar;

    @Inject
    EventosProximosUseCase proximos;

    /**
     * Traz eventos da NASA para a base local.
     *
     * <p>POST porque ESCREVE. Idempotente por {@code eonetId}: repetir não duplica.</p>
     */
    @POST
    @Path("/sincronizar")
    public SincronizarEventosUseCase.Resultado sincronizar(
            @QueryParam("limite") @DefaultValue("50") int limite,
            @QueryParam("dias") @DefaultValue("30") int dias,
            @QueryParam("apenasAtivos") @DefaultValue("true") boolean apenasAtivos) {
        return sincronizar.executar(limite, dias, apenasAtivos);
    }

    @GET
    public List<EventoResposta> listar(@QueryParam("pagina") @DefaultValue("0") int pagina,
                                       @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return EventoResposta.de(consultar.listar(pagina, tamanho));
    }

    @GET
    @Path("/categoria/{categoria}")
    public List<EventoResposta> porCategoria(@PathParam("categoria") String categoria,
                                             @QueryParam("pagina") @DefaultValue("0") int pagina,
                                             @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return EventoResposta.de(consultar.porCategoria(categoria, pagina, tamanho));
    }

    @GET
    @Path("/{id}")
    public Response porId(@PathParam("id") long id) {
        return consultar.porId(id)
                .map(e -> Response.ok(EventoResposta.de(e)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/eonet/{eonetId}")
    public Response porEonetId(@PathParam("eonetId") String eonetId) {
        return consultar.porEonetId(eonetId)
                .map(e -> Response.ok(EventoResposta.de(e)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Os eventos ativos dentro do raio, do mais perto para o mais longe.
     *
     * <p>É a consulta que dispara o alerta. Duas etapas: caixa por índice, geodésia
     * decidindo — porque o canto da caixa fica 41% além do raio pedido.</p>
     */
    @GET
    @Path("/proximos")
    public List<EventoProximoResposta> proximos(@QueryParam("latitude") double latitude,
                                                @QueryParam("longitude") double longitude,
                                                @QueryParam("raioKm") @DefaultValue("100") double raioKm,
                                                @QueryParam("dias") @DefaultValue("30") int dias) {
        return proximos.executar(new Coordenada(latitude, longitude), raioKm, dias).stream()
                .map(p -> new EventoProximoResposta(
                        EventoResposta.de(p.evento()),
                        Math.round(p.distanciaKm() * 10.0) / 10.0))
                .toList();
    }

    /** Contagem por categoria, para a tela de estatísticas. */
    @GET
    @Path("/estatisticas/categorias")
    public List<RepositorioDeEventosPort.ContagemPorCategoria> estatisticas(
            @QueryParam("dias") @DefaultValue("30") int dias) {
        return consultar.contarPorCategoria(dias);
    }

    @GET
    @Path("/resumo")
    public Resumo resumo() {
        return new Resumo(consultar.contar(), consultar.contarAtivos());
    }

    /** Quantos eventos há, e quantos ainda estão acontecendo. */
    public record Resumo(long total, long ativos) {
    }

    /**
     * O evento como a API devolve.
     *
     * <p>Sem o {@code jsonOriginal}: é cópia forense, pode ter quilobytes, e multiplicá-lo
     * por vinte eventos numa listagem faria a resposta pesar sem servir a ninguém. Quem
     * precisa dele lê o evento por id, no banco.</p>
     */
    public record EventoResposta(Long id, String eonetId, String titulo, String categoria,
                                 String ocorridoEm, Double latitude, Double longitude,
                                 boolean ativo, String encerradoEm,
                                 boolean participaDoAlertaDeProximidade,
                                 String motivoForaDoAlerta, String sincronizadoEm) {

        static EventoResposta de(EventoNatural e) {
            var c = e.coordenada();
            return new EventoResposta(e.id(), e.eonetId(), e.titulo(), e.categoria(),
                    e.ocorridoEm().toString(),
                    c == null ? null : c.latitude(),
                    c == null ? null : c.longitude(),
                    e.ativo(),
                    e.encerradoEm() == null ? null : e.encerradoEm().toString(),
                    e.participaDoAlertaDeProximidade(),
                    e.motivoForaDoAlerta(),
                    e.sincronizadoEm() == null ? null : e.sincronizadoEm().toString());
        }

        static List<EventoResposta> de(List<EventoNatural> eventos) {
            return eventos.stream().map(EventoResposta::de).toList();
        }
    }

    /** Um evento e a distância dele, já calculada no servidor. */
    public record EventoProximoResposta(EventoResposta evento, double distanciaKm) {
    }
}
