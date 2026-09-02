package org.nasa.painel.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.painel.domain.Noticia;
import org.nasa.painel.domain.exceptions.NoticiarioIndisponivelException;
import org.nasa.painel.domain.ports.FonteDeNoticiasPort;

import java.util.List;

/**
 * As noticias que a home mostra — e o que fazer quando nao ha nenhuma.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O noticiário é <b>vitrine</b>: dá contexto a quem chega
 * e mostra que os dados são reais e recentes. Não é função do sistema, e essa distinção
 * decide tudo aqui.</p>
 *
 * <p><b>FALHA ABERTA, DE PROPÓSITO.</b> Se a fonte cair, a home continua inteira e diz
 * que o noticiário está indisponível. Derrubar a página inicial de um projeto que é
 * vitrine porque um feed de terceiro piscou seria a troca mais cara possível — e foi
 * exatamente o que aconteceria com o legado hoje, cuja fonte está morta.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Três estados, nunca dois.</b> "Tem notícias", "a fonte respondeu e não há
 *       nada" e "a fonte está fora" pedem textos diferentes na tela. Colapsar os dois
 *       últimos em "sem notícias" faria uma queda parecer um mundo em paz.</li>
 *   <li><b>Nunca lança para a tela.</b> A exceção morre aqui, virando estado.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Devolve {@link Resultado} com a lista vazia e
 * {@code fonteIndisponivel = true}. O log registra em WARN — para que uma fonte morta seja
 * percebida antes de alguém reparar que o carrossel some há semanas.</p>
 */
@ApplicationScoped
public class ConsultarNoticiasUseCase {

    private static final Logger LOG = Logger.getLogger(ConsultarNoticiasUseCase.class);
    private static final String OPERACAO = "consultar-noticias";

    @Inject
    FonteDeNoticiasPort fonte;

    /**
     * O que a consulta trouxe, e em que estado.
     *
     * @param noticias           o que mostrar; vazia quando não há ou quando a fonte caiu
     * @param fonteIndisponivel  a diferença entre "não há notícias" e "não consegui saber"
     * @param motivo             texto para a tela quando algo não saiu como esperado
     */
    public record Resultado(List<Noticia> noticias, boolean fonteIndisponivel, String motivo) {

        public boolean vazio() {
            return noticias.isEmpty();
        }
    }

    public Resultado executar(int limite) {
        try {
            List<Noticia> noticias = fonte.maisRecentes(Math.max(1, limite));
            if (noticias.isEmpty()) {
                // A fonte RESPONDEU e nao havia nada. E diferente de ela estar fora.
                return new Resultado(List.of(), false,
                        "a fonte respondeu, e nao ha eventos publicados agora");
            }
            return new Resultado(noticias, false, null);
        } catch (NoticiarioIndisponivelException fora) {
            // WARN, e nao INFO: fonte externa morre sem avisar, e o sintoma e um carrossel
            // que some. Sem esta linha, alguem so repara semanas depois.
            LOG.warn(Registro.recusa(OPERACAO, "fonte", "NOTICIARIO_INDISPONIVEL"), fora);
            return new Resultado(List.of(), true,
                    "o noticiario esta indisponivel no momento; o resto do sistema nao depende dele");
        }
    }
}
