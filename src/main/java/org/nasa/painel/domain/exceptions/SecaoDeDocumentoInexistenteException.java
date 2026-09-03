package org.nasa.painel.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Um documento aponta para uma seção que não está no catálogo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A tela do documento desenha a trilha de navegação e a
 * cor do título a partir da seção. Sem ela não há como montar a página — e o Qute deste
 * projeto é <b>estrito</b>: chave ausente é 500, não espaço em branco.</p>
 *
 * <p><b>POR QUE É UMA EXCEÇÃO PRÓPRIA, e não {@code IllegalStateException}.</b> Porque
 * este erro é de <b>catálogo</b>, não de quem navegou: o documento existe, o arquivo existe,
 * e o que está errado é a linha que declara a que seção ele pertence. Genérica, ela cairia
 * na página de "algo quebrou aqui dentro" sem dizer <i>o quê</i>, e quem fosse investigar
 * começaria pelo banco. Nomeada, o log já traz o slug e a seção órfã.</p>
 *
 * <p><b>Ela não deveria acontecer em produção nunca</b> — é um erro de digitação no
 * catálogo, que o teste do catálogo pega antes. Existe para o caso de o teste ser removido
 * junto com a linha errada, o que é exatamente quando ninguém está olhando.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É o próprio: carrega
 * {@link CausaRaiz#CONFLITO_DE_ESTADO} — o catálogo declara algo que ele mesmo não tem.</p>
 */
public class SecaoDeDocumentoInexistenteException extends ErroDePipeline {

    public SecaoDeDocumentoInexistenteException(String slug, String secaoDeclarada) {
        super("ler-documentacao", slug, CausaRaiz.CONFLITO_DE_ESTADO,
              "o documento '" + slug + "' declara a secao '" + secaoDeclarada
                      + "', que nao existe no catalogo");
    }
}
