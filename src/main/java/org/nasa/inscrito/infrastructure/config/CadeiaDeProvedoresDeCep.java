package org.nasa.inscrito.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.inscrito.domain.ports.CadeiaDeProvedoresDeCepPort;
import org.nasa.inscrito.domain.ports.ConsultaCepPort;
import org.nasa.inscrito.infrastructure.adapters.BrasilApiCepAdapter;
import org.nasa.inscrito.infrastructure.adapters.ViaCepAdapter;

import java.util.List;

/**
 * A ordem concreta dos provedores de CEP.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Este é o único lugar do sistema que sabe <b>quais</b>
 * provedores existem e em que sequência. Acrescentar um terceiro é editar esta lista;
 * nenhum caso de uso muda.</p>
 *
 * <p><b>A ORDEM, E O MOTIVO MEDIDO</b> (2026-09-02):</p>
 * <ol>
 *   <li><b>BrasilAPI</b> — 297 bytes em <b>0,23 s</b>, e traz a coordenada junto;</li>
 *   <li><b>ViaCEP</b> — <b>1,04 s</b> e <b>sem</b> coordenada; entra só se o primeiro
 *       falhar.</li>
 * </ol>
 *
 * <p><b>INVARIANTE.</b> A lista é literal e ordenada. Iterar sobre
 * {@code Instance<ConsultaCepPort>} pareceria mais elegante e seria um defeito com data
 * marcada: a ordem de resolução do CDI não é garantida.</p>
 *
 * <p><b>FALHA.</b> Não falha: é configuração. Provedor fora do ar é problema do caso de
 * uso, que cai para o seguinte da lista.</p>
 */
@ApplicationScoped
public class CadeiaDeProvedoresDeCep implements CadeiaDeProvedoresDeCepPort {

    @Inject
    BrasilApiCepAdapter brasilApi;

    @Inject
    ViaCepAdapter viaCep;

    @Override
    public List<ConsultaCepPort> emOrdem() {
        return List.of(brasilApi, viaCep);
    }
}
