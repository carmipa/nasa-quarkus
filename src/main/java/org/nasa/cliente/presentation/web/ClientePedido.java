package org.nasa.cliente.presentation.web;

import org.nasa.cliente.domain.exceptions.ClienteInvalidoException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * O que chega do cliente HTTP para criar ou alterar um cadastro.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o contrato de fio, e é deliberadamente <b>separado</b>
 * do record de domínio. Se o domínio fosse o corpo da requisição, qualquer campo novo
 * dele viraria API pública sem ninguém decidir isso — e qualquer renomeação quebraria
 * clientes externos.</p>
 *
 * <p><b>INVARIANTES.</b></p>
 * <ol>
 *   <li><b>A data chega como texto ISO-8601</b> ({@code AAAA-MM-DD}) e é convertida aqui,
 *       na borda. Texto malformado vira 400 com mensagem que diz o formato esperado — não
 *       um erro de desserialização ilegível.</li>
 *   <li><b>Nenhuma regra de negócio.</b> Este record não valida nome nem documento: quem
 *       valida é o domínio, e duplicar a regra aqui criaria duas versões dela.</li>
 * </ol>
 *
 * <p><b>FALHA.</b> Data ausente ou fora do formato ⇒ {@link ClienteInvalidoException},
 * que a borda traduz para 400.</p>
 */
public record ClientePedido(String nome, String sobrenome,
                            String dataNascimento, String documento) {

    /**
     * A data como {@link LocalDate}.
     *
     * <p><b>FALHA:</b> texto vazio ou fora de {@code AAAA-MM-DD} lança
     * {@link ClienteInvalidoException} nomeando o campo e o formato — mensagem de
     * desserialização crua não diz ao operador o que digitar.</p>
     */
    public LocalDate dataNascimentoComoData() {
        if (dataNascimento == null || dataNascimento.isBlank()) {
            throw new ClienteInvalidoException("dataNascimento", "ausente");
        }
        try {
            return LocalDate.parse(dataNascimento.trim());
        } catch (DateTimeParseException e) {
            throw new ClienteInvalidoException("dataNascimento",
                    "esperado AAAA-MM-DD, recebi " + dataNascimento);
        }
    }
}
