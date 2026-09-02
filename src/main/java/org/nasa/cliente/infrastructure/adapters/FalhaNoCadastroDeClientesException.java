package org.nasa.cliente.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operação do cadastro de clientes.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa falha de <b>infraestrutura</b> de recusa de
 * <b>negócio</b>. A distinção não é acadêmica: documento duplicado o operador resolve
 * sozinho (é 409, e a tela diz o que fazer); banco indisponível ele não resolve, e a
 * resposta certa é 503 com o incidente registrado para quem cuida do sistema.</p>
 *
 * <p><b>INVARIANTE.</b> Só chega aqui o que <b>não</b> tem tradução de negócio. A
 * violação de unicidade do documento é interceptada antes e vira
 * {@code DocumentoJaCadastradoException} — misturar as duas faria o painel contar
 * digitação repetida como falha de banco, e o número de incidentes perderia o sentido.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#PERSISTENCIA_FALHOU}, com a operação e o
 * alvo nomeados. A causa técnica original vai como {@code cause}: perder o motivo é o
 * defeito que a planta mais cobra.</p>
 */
public class FalhaNoCadastroDeClientesException extends ErroDePipeline {
    public FalhaNoCadastroDeClientesException(String operacao, String alvo, Throwable causaTecnica) {
        super("cliente-" + operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a operacao de cadastro", causaTecnica);
    }
}
