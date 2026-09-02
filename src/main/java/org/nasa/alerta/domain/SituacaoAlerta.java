package org.nasa.alerta.domain;

import org.nasa.alerta.domain.exceptions.SituacaoDeAlertaDesconhecidaException;

/**
 * Em que pe esta o aviso.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> São os três estados do padrão <i>outbox</i>: o aviso é
 * <b>registrado</b> antes de ser enviado, e só depois marcado como entregue ou falho.
 * Essa separação é o que torna o envio seguro de repetir — porque a linha já existe no
 * banco antes de qualquer coisa sair.</p>
 *
 * <p><b>POR QUE TRÊS, E NÃO DOIS.</b> Um sistema com "enviado / não enviado" não sabe
 * distinguir "ainda não tentei" de "tentei e falhou". A diferença decide o que fazer:
 * o primeiro se envia, o segundo se investiga. Sem ela, uma falha permanente vira uma
 * tentativa infinita — ou, pior, um aviso que ninguém percebe que nunca saiu.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> {@link #ENVIADO} e {@link #FALHOU} são terminais e
 * exigem instante de conclusão — o {@code CHECK alerta_terminal_tem_instante} do esquema
 * garante isso no banco. "Enviado" sem quando deixa a auditoria sem fechar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Valor desconhecido lança
 * {@link SituacaoDeAlertaDesconhecidaException} — o mesmo conjunto fechado que o
 * {@code CHECK} do banco exige.</p>
 */
public enum SituacaoAlerta {

    /** Registrado, ainda não despachado. É o estado em que todo alerta nasce. */
    PENDENTE,

    /** Saiu. Instante de conclusão obrigatório. */
    ENVIADO,

    /** Tentou e não foi. Instante obrigatório, e a causa-raiz preenchida. */
    FALHOU;

    public static SituacaoAlerta de(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new SituacaoDeAlertaDesconhecidaException("(vazio)");
        }
        String limpo = texto.strip().toUpperCase();
        for (SituacaoAlerta s : values()) {
            if (s.name().equals(limpo)) {
                return s;
            }
        }
        throw new SituacaoDeAlertaDesconhecidaException(texto);
    }

    /** Estado terminal exige instante de conclusão — o banco também cobra. */
    public boolean terminal() {
        return this != PENDENTE;
    }

    public String rotulo() {
        return switch (this) {
            case PENDENTE -> "Pendente";
            case ENVIADO -> "Enviado";
            case FALHOU -> "Falhou";
        };
    }
}
