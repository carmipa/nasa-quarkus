package org.nasa.alerta.domain;

import org.nasa.alerta.domain.exceptions.AlertaInvalidoException;

import java.time.Instant;
import java.util.Optional;

/**
 * O aviso de que um desastre aconteceu perto de alguém.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a saída de todo o sistema. Tudo o mais — cadastro,
 * endereço, contato, sincronização com a NASA, geodésia — existe para produzir esta linha
 * e entregá-la.</p>
 *
 * <p><b>É UM REGISTRO DE OUTBOX, E ISSO É O DESENHO.</b> O alerta é <b>gravado antes de
 * ser enviado</b>, como {@code PENDENTE}, e só depois marcado. A ordem inversa — enviar e
 * depois gravar — perde o registro se o processo cair entre as duas coisas: a pessoa
 * recebeu o aviso e o sistema não sabe, então avisa de novo na próxima varredura. Gravar
 * primeiro troca "avisar duas vezes" por "no pior caso, avisar com atraso", que é a troca
 * certa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-ALERTA-001 — o mesmo evento não avisa o mesmo cliente duas vezes.</b> A
 *       chave de idempotência é {@code (cliente_id, evento_id)} e mora no <b>banco</b>,
 *       não na memória de um processo que reinicia. Uma tempestade que dura cinco dias
 *       aparece em cinco varreduras; sem esta chave, são cinco avisos.</li>
 *   <li><b>Estado terminal exige instante de conclusão.</b> O {@code CHECK} do esquema
 *       cobra. "Enviado" sem quando deixa a auditoria sem fechar.</li>
 *   <li><b>{@code causaRaiz} preenchida quando falhou</b>, e só então. É o que permite
 *       responder "por que este aviso não saiu?" sem abrir o log de três semanas atrás.</li>
 *   <li><b>{@code tentativas} nunca decresce.</b> É contador de esforço, e serve para
 *       distinguir "falhou uma vez" de "falha sempre" — que pedem reações diferentes.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link AlertaInvalidoException} com o nome do
 * campo. O destino nunca entra na mensagem de erro: é o e-mail de uma pessoa.</p>
 *
 * @param id           nulo enquanto não gravado
 * @param clienteId    quem é avisado
 * @param eventoId     o evento que motivou
 * @param destino      para onde vai — o e-mail do contato de emergência
 * @param situacao     PENDENTE, ENVIADO ou FALHOU
 * @param causaRaiz    preenchida somente quando FALHOU
 * @param tentativas   quantas vezes se tentou despachar
 * @param criadoEm     quando o aviso foi registrado
 * @param concluidoEm  quando terminou; obrigatório em estado terminal
 */
public record Alerta(Long id, long clienteId, long eventoId, String destino,
                     SituacaoAlerta situacao, String causaRaiz, int tentativas,
                     Instant criadoEm, Instant concluidoEm) {

    public Alerta {
        if (destino == null || destino.isBlank()) {
            throw new AlertaInvalidoException("destino",
                    "ausente — sem destino o aviso nao chega a lugar nenhum");
        }
        if (situacao == null) {
            throw new AlertaInvalidoException("situacao", "ausente");
        }
        if (tentativas < 0) {
            throw new AlertaInvalidoException("tentativas", "negativa");
        }
        // A mesma regra do CHECK do esquema, dita aqui também: a de baixo vale para quem
        // escreve por outro caminho, esta vale para quem monta o objeto errado no código.
        if (situacao.terminal() && concluidoEm == null) {
            throw new AlertaInvalidoException("concluidoEm",
                    "situacao " + situacao + " e terminal e exige instante de conclusao");
        }
        destino = destino.strip();
    }

    /** Um aviso recém-registrado, ainda por despachar. */
    public static Alerta pendente(long clienteId, long eventoId, String destino) {
        return new Alerta(null, clienteId, eventoId, destino, SituacaoAlerta.PENDENTE,
                null, 0, null, null);
    }

    /** O mesmo aviso, entregue. */
    public Alerta entregue(Instant quando) {
        return new Alerta(id, clienteId, eventoId, destino, SituacaoAlerta.ENVIADO,
                null, tentativas + 1, criadoEm, quando);
    }

    /**
     * O mesmo aviso, falho.
     *
     * <p>A causa-raiz vem junto: sem ela, "falhou" obriga a abrir o log de semanas atrás
     * para descobrir se foi o servidor de e-mail, o endereço inválido, ou nós.</p>
     */
    public Alerta falho(Instant quando, String causa) {
        return new Alerta(id, clienteId, eventoId, destino, SituacaoAlerta.FALHOU,
                causa, tentativas + 1, criadoEm, quando);
    }

    public Optional<Instant> concluidoEmOpcional() {
        return Optional.ofNullable(concluidoEm);
    }

    public Optional<String> causaRaizOpcional() {
        return Optional.ofNullable(causaRaiz);
    }

    public boolean pendente() {
        return situacao == SituacaoAlerta.PENDENTE;
    }

    /**
     * O destino com o suficiente à vista para conferir, e escondido para não vazar.
     *
     * <p>{@code paulo@exemplo.com} vira {@code pa***@exemplo.com}. Dá para reconhecer o
     * destinatário ao investigar, e não dá para colher o endereço de um print — e esta
     * informação aparece na tela de auditoria, que é justamente a que alguém abre para
     * mostrar a outra pessoa.</p>
     *
     * <p><b>Mora no domínio, e não no adaptador de envio</b>, porque é regra sobre como o
     * destino se expõe: o log precisa dela e a borda também. Deixá-la na infraestrutura
     * obrigaria a camada de apresentação a importar infraestrutura para exibir um campo.</p>
     */
    public String destinoMascarado() {
        return mascarar(destino);
    }

    /** A regra de mascaramento, isolada para o teste exercitar as bordas. */
    public static String mascarar(String email) {
        if (email == null || email.isBlank()) {
            return "(sem destino)";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        String local = email.substring(0, arroba);
        String visivel = local.length() <= 2 ? local : local.substring(0, 2);
        return visivel + "***" + email.substring(arroba);
    }
}
