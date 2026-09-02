package org.nasa.persistencia.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.persistencia.domain.Migracao;
import org.nasa.persistencia.domain.exceptions.MigracaoAlteradaException;
import org.nasa.persistencia.domain.ports.FonteDeMigracoesPort;
import org.nasa.persistencia.domain.ports.RegistroDeMigracoesPort;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Leva o banco da versão em que ele está até a versão que o código espera.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Toda máquina que rodar este sistema — a de quem
 * desenvolve, o contêiner, a do professor que for corrigir — precisa terminar com
 * <b>exatamente o mesmo esquema</b>. Este caso de uso é o que garante isso sem ninguém
 * rodar SQL à mão.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Migração já aplicada e editada ABORTA o boot</b> ({@link MigracaoAlteradaException}).
 *       É a invariante mais importante daqui, e a única que não tem cura automática.</li>
 *   <li><b>Ordem crescente, sem pular.</b> A fonte entrega ordenado; este caso de uso
 *       aplica na ordem recebida e para no primeiro erro.</li>
 *   <li><b>Idempotente.</b> Rodar duas vezes seguidas aplica zero na segunda — e diz isso
 *       com número, não com silêncio.</li>
 *   <li><b>Conta o que AGIU e o que se ABSTEVE.</b> "Nenhuma pendente" e "não consegui
 *       ler nenhuma" não podem produzir a mesma linha de log.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha <b>fechada e alta</b>: a exceção sobe e
 * o boot cai. É deliberado — subir a aplicação sobre um esquema incompleto faz a primeira
 * consulta falhar longe da causa, possivelmente depois de já ter gravado dado errado.
 * Nada fica pela metade: cada migração roda na própria transação (invariante da porta).</p>
 */
@ApplicationScoped
public class AplicadorDeMigracoes {

    private static final Logger LOG = Logger.getLogger(AplicadorDeMigracoes.class);
    private static final String OPERACAO = "migrar-banco";

    @Inject
    FonteDeMigracoesPort fonte;

    @Inject
    RegistroDeMigracoesPort registro;

    @Inject
    Relogio relogio;

    /**
     * O que a migração fez.
     *
     * @param disponiveis quantas migrações o índice declara
     * @param aplicadas   o que AGIU nesta execução
     * @param jaEstavam   o que se ABSTEVE — já estava no banco
     */
    public record Resultado(int disponiveis, int aplicadas, int jaEstavam) {
    }

    public Resultado executar() {
        var inicio = relogio.agora();

        registro.prepararControle();
        List<Migracao> declaradas = fonte.disponiveis();
        Map<Integer, String> aplicadasNoBanco = registro.checksumsAplicados();

        // 1. IMUTABILIDADE — conferir TUDO antes de aplicar QUALQUER COISA.
        //    Se a V003 foi editada, abortar depois de já ter aplicado a V007 deixaria o
        //    banco num estado que ninguém pediu. A verificação vem inteira, primeiro.
        for (Migracao m : declaradas) {
            String registrado = aplicadasNoBanco.get(m.versao());
            if (registrado != null && !registrado.equals(m.checksum())) {
                throw new MigracaoAlteradaException(m.identificacao(), registrado, m.checksum());
            }
        }

        // 2. Aplicar o que falta, na ordem.
        int aplicadas = 0;
        int jaEstavam = 0;
        for (Migracao m : declaradas) {
            if (aplicadasNoBanco.containsKey(m.versao())) {
                jaEstavam++;
                continue;
            }
            LOG.info(Registro.de(OPERACAO, m.identificacao(), "aplicando"));
            registro.aplicarERegistrar(m);
            aplicadas++;
        }

        var r = new Resultado(declaradas.size(), aplicadas, jaEstavam);
        LOG.info(Registro.de(OPERACAO, "esquema",
                "declaradas=" + r.disponiveis() + " aplicadas=" + r.aplicadas()
                        + " jaEstavam=" + r.jaEstavam(),
                Duration.between(inicio, relogio.agora())));

        // Alarme do job silencioso: índice vazio não é "nada a fazer", é índice quebrado.
        if (r.disponiveis() == 0) {
            LOG.warn(Registro.recusa(OPERACAO, "esquema", "NENHUMA_MIGRACAO_DECLARADA"));
        }
        return r;
    }
}
