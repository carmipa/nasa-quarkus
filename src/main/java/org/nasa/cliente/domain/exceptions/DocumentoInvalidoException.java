package org.nasa.cliente.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O documento recebido não tem forma de CPF nem de CNPJ.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O documento é a chave de identidade e a base do
 * {@code UNIQUE} do banco. Aceitar texto livre faria a unicidade proteger nada: dois
 * cadastros com "a definir" seriam a mesma pessoa aos olhos do banco, e o terceiro seria
 * recusado sem que ninguém entendesse por quê.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada na construção do valor. A validação é de
 * <b>forma</b>, não de autenticidade — decisão declarada no Javadoc de {@code Documento}.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}. O valor recusado vai no
 * alvo, para a tela conseguir mostrar o que foi digitado.</p>
 */
public class DocumentoInvalidoException extends ErroDePipeline {
    public DocumentoInvalidoException(String valorRecebido, String motivo) {
        super("validar-documento", valorRecebido, CausaRaiz.DADO_INVALIDO, motivo);
    }
}
