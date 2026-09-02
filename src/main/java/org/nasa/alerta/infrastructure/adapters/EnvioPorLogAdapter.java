package org.nasa.alerta.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.ports.EnvioDeAlertaPort;
import org.nasa.core.log.Registro;

/**
 * Registra o aviso no log, porque ainda nao ha servidor de e-mail.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Permite que o fluxo inteiro de alerta seja construído,
 * testado e demonstrado <b>antes</b> de existir SMTP configurado — que depende de conta,
 * domínio e credencial, e nenhum dos três existe hoje.</p>
 *
 * <p><b>ESTA LACUNA É DECLARADA EM VOZ ALTA, e é o ponto principal desta classe.</b>
 * Enquanto este adaptador estiver em uso, <b>ninguém recebe aviso nenhum</b>. O sistema
 * registra que teria enviado, e é só isso. Por isso:</p>
 * <ul>
 *   <li>{@link #entregaDeVerdade()} devolve {@code false}, e a API expõe esse campo;</li>
 *   <li>cada envio sai em <b>WARN</b>, não em INFO — para que a linha se destaque no log
 *       e ninguém conclua, ao ver "ENVIADO" na tela, que a pessoa foi avisada.</li>
 * </ul>
 * <p>Um adaptador que fingisse sucesso silencioso seria <b>pior que não ter alerta
 * nenhum</b>: a tela mostraria cobertura que não existe, e a descoberta viria no dia do
 * desastre.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O destino aparece <b>parcialmente mascarado</b> no
 * log. É o e-mail de uma pessoa e o log é lido, copiado e colado; mas mascarar por
 * inteiro impediria conferir para onde o aviso foi, que é justamente o que se quer saber
 * ao investigar.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não falha: escrever no log não tem como dar
 * errado de forma recuperável. Quando o adaptador de SMTP existir, ele lançará exceção com
 * causa-raiz, e o despacho marcará o alerta como {@code FALHOU} com a causa.</p>
 */
@ApplicationScoped
public class EnvioPorLogAdapter implements EnvioDeAlertaPort {

    private static final Logger LOG = Logger.getLogger(EnvioPorLogAdapter.class);

    @Override
    public void enviar(Alerta alerta, String assunto, String mensagem) {
        // WARN, e nao INFO, de proposito: enquanto este adaptador estiver ligado, a tela
        // dira "ENVIADO" e ninguem tera recebido nada. A linha precisa se destacar.
        LOG.warn(Registro.de("enviar-alerta", alerta.destinoMascarado(),
                "SEM SERVIDOR DE E-MAIL: o aviso NAO foi entregue, so registrado. "
                        + "assunto=" + assunto));
        LOG.info(Registro.de("enviar-alerta", alerta.destinoMascarado(),
                "corpo: " + mensagem));
    }

    @Override
    public String descricaoDoMeio() {
        return "registro em log (nenhum servidor de e-mail configurado)";
    }

    @Override
    public boolean entregaDeVerdade() {
        return false;
    }

}
