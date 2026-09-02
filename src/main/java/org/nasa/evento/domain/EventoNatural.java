package org.nasa.evento.domain;

import org.nasa.evento.domain.exceptions.EventoInvalidoException;
import org.nasa.geo.domain.Coordenada;

import java.time.Instant;
import java.util.Optional;

/**
 * Um evento natural publicado pela NASA — o motivo pelo qual este sistema existe.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que dispara o alerta. Um incêndio, uma tempestade,
 * um vulcão: quando um deles acontece perto de um endereço cadastrado, alguém precisa ser
 * avisado. Tudo o mais no sistema existe para servir a esta comparação.</p>
 *
 * <p><b>O DEFEITO MAIS CARO DO LEGADO, MEDIDO EM 02/09/2026.</b> A EONET devolve
 * <b>vários</b> pontos de geometria por evento — a trajetória, com uma data por ponto. O
 * legado usava {@code getGeometry().get(0)}: o <b>primeiro</b>, que é onde o evento
 * COMEÇOU. Medido na resposta real da API, no evento {@code EONET_23800} (Tropical Storm
 * Marie), com seis pontos:</p>
 * <pre>
 * primeiro ponto  2026-09-01T06:00Z   lat  14.10  lon -108.10
 * último ponto    2026-09-02T12:00Z   lat  16.80  lon -111.30
 * distância ....................................  456 km
 * </pre>
 * <p>Num alerta de raio 100 km, isso é errar por 456 km — avisar quem está longe e não
 * avisar quem está perto, sem nenhum erro aparecer. <b>Aqui a posição é sempre a do ponto
 * MAIS RECENTE</b>, que é onde o evento está agora.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O {@code eonetId} identifica UM evento.</b> Protegido por {@code UNIQUE} no
 *       banco. No legado a garantia morava só no Java
 *       ({@code findByEonetIdApi().orElse(new)}), e duas sincronizações simultâneas liam
 *       "não existe" e inseriam as duas — evento duplicado inflando estatística e mapa,
 *       sem erro nenhum.</li>
 *   <li><b>Coordenada ausente é AUSENTE.</b> Nem todo evento da EONET tem geometria de
 *       ponto; alguns são polígonos, e alguns não têm nenhuma. Evento sem posição não
 *       entra no alerta de proximidade, e a tela diz isso — nunca vira {@code (0,0)}.</li>
 *   <li><b>{@code encerradoEm} nulo significa ATIVO.</b> É o estado de todo evento no
 *       momento em que aparece. Só evento ativo dispara alerta: um incêndio apagado há
 *       três semanas não deve avisar ninguém.</li>
 *   <li><b>{@code jsonOriginal} é cópia forense</b> e pode ser grande. Existe para
 *       responder, meses depois, "o que exatamente a NASA mandou?" quando um evento
 *       parecer errado.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link EventoInvalidoException} com o nome do
 * campo. Evento sem {@code eonetId} ou sem título é recusado: sem identificador não há
 * como deduplicar, e sem título não há o que mostrar em tela nem no aviso.</p>
 *
 * @param id             nulo enquanto não gravado
 * @param eonetId        o identificador da NASA, ex.: {@code EONET_23800}
 * @param titulo         o nome do evento, como a NASA publica
 * @param categoria      a categoria principal, ex.: {@code severeStorms}
 * @param ocorridoEm     instante do ponto MAIS RECENTE — onde o evento está agora
 * @param coordenada     posição atual; ausente quando a NASA não deu ponto
 * @param jsonOriginal   cópia forense da resposta
 * @param sincronizadoEm quando este registro foi lido da NASA
 * @param encerradoEm    quando a NASA marcou como encerrado; nulo = ATIVO
 */
public record EventoNatural(Long id, String eonetId, String titulo, String categoria,
                            Instant ocorridoEm, Coordenada coordenada, String jsonOriginal,
                            Instant sincronizadoEm, Instant encerradoEm) {

    public EventoNatural {
        eonetId = exigir(eonetId, "eonetId");
        titulo = exigir(titulo, "titulo");
        if (ocorridoEm == null) {
            throw new EventoInvalidoException("ocorridoEm", "ausente");
        }
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new EventoInvalidoException(campo, "ausente");
        }
        return valor.strip();
    }

    /** Recém-lido da NASA: id e instante de sincronização vêm da gravação. */
    public static EventoNatural lidoDaNasa(String eonetId, String titulo, String categoria,
                                           Instant ocorridoEm, Coordenada coordenada,
                                           String jsonOriginal, Instant encerradoEm) {
        return new EventoNatural(null, eonetId, titulo, categoria, ocorridoEm, coordenada,
                jsonOriginal, null, encerradoEm);
    }

    public Optional<Coordenada> coordenadaOpcional() {
        return Optional.ofNullable(coordenada);
    }

    public Optional<Instant> encerradoEmOpcional() {
        return Optional.ofNullable(encerradoEm);
    }

    /**
     * Se este evento ainda pode gerar alerta.
     *
     * <p>Duas condições, e as duas são necessárias: estar <b>ativo</b> e ter
     * <b>posição</b>. Evento encerrado não avisa ninguém; evento sem coordenada não tem
     * como ser comparado com endereço nenhum — e é essa segunda ausência que a tela
     * precisa declarar, porque ela é invisível de outra forma.</p>
     */
    public boolean participaDoAlertaDeProximidade() {
        return encerradoEm == null && coordenada != null;
    }

    /** Por que este evento não entra no alerta, ou {@code null} se entra. */
    public String motivoForaDoAlerta() {
        if (encerradoEm != null) {
            return "evento encerrado em " + encerradoEm;
        }
        if (coordenada == null) {
            return "a NASA nao publicou coordenada de ponto para este evento";
        }
        return null;
    }

    public boolean ativo() {
        return encerradoEm == null;
    }
}
