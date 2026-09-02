package org.nasa.persistencia.domain.ports;

import org.nasa.persistencia.domain.Migracao;

import java.util.List;

/**
 * De onde vêm as migrações, e em que ordem.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Isola o caso de uso da forma como os arquivos são
 * guardados. Hoje é um índice no classpath; se um dia for outra coisa, a regra de
 * aplicação não muda.</p>
 *
 * <p><b>INVARIANTE.</b> A lista vem <b>ordenada por versão, crescente</b>, e a ordem é
 * declarada, nunca deduzida de varredura de diretório: varredura muda de resultado entre
 * a IDE e o jar, e ordem de DDL que depende do empacotamento é defeito com data marcada.</p>
 *
 * <p><b>FALHA.</b> Índice ausente ou arquivo listado que não existe ⇒ exceção específica.
 * Devolver lista vazia calada seria pior: o sistema subiria dizendo "nenhuma migração
 * pendente" com o banco sem tabela nenhuma.</p>
 */
public interface FonteDeMigracoesPort {

    /** Todas as migrações declaradas, em ordem crescente de versão. */
    List<Migracao> disponiveis();
}
