package org.nasa.painel.presentation.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import io.agroal.api.AgroalDataSource;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Diz se o sistema está de pé — para quem observa de fora.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Numa VPS, alguma coisa precisa decidir se este processo
 * deve ser reiniciado: o Docker, o systemd, um monitor externo. Sem um endereço que responda
 * essa pergunta, a decisão vira "a porta 8080 aceita conexão" — e uma aplicação com o banco
 * inacessível aceita conexão e devolve erro em toda página. <b>Ela parece viva e não está.</b></p>
 *
 * <p><b>O QUE ELE CHECA, E POR QUE SÓ ISSO.</b> Uma consulta ao banco. É a única dependência
 * cuja ausência torna o sistema inútil: sem NASA o alerta decide sobre a base local, sem
 * GDACS a home perde o carrossel, sem BrasilAPI a inscrição entra sem coordenada — todas
 * degradam. Sem banco, nada funciona.</p>
 *
 * <p><b>POR QUE NÃO CHECAR AS FONTES EXTERNAS.</b> Seria a armadilha clássica: a NASA fora
 * do ar derrubaria a saúde, o orquestrador reiniciaria o processo, e o reinício não traria a
 * NASA de volta. Um sistema que se reinicia em laço por causa de um provedor de terceiro é
 * pior que um sistema degradado — e a telemetria já mostra as fontes que falham.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nenhum dado sensível na resposta.</b> Este endereço é público por natureza — o
 *       monitor não se autentica. Nada de caminho de arquivo, credencial, versão de
 *       biblioteca ou contagem que revele volume de negócio.</li>
 *   <li><b>Falha é 503, não 200 com um campo dizendo "ruim".</b> Monitor lê <b>status</b>;
 *       muitos nem olham o corpo. Um 200 com {@code "estado":"FORA"} é indistinguível de
 *       saudável para metade das ferramentas que existem.</li>
 *   <li><b>Rápido e sem efeito.</b> É chamado a cada poucos segundos, para sempre. Uma
 *       checagem cara vira carga permanente; uma que escreve vira lixo permanente.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Banco fora ⇒ <b>503</b> com o motivo
 * classificado, sem detalhe técnico. O erro completo vai para o log, onde tem dono.</p>
 */
@Path("/saude")
@Produces(MediaType.APPLICATION_JSON)
public class SaudeResource {

    private static final Logger LOG = Logger.getLogger(SaudeResource.class);
    private static final String OPERACAO = "verificar-saude";

    /**
     * O banco, direto.
     *
     * <p><b>Não é um caso de uso de fatia, e isso é arquitetura, não preguiça.</b> A
     * primeira versão desta classe injetava {@code ConsultarEventosUseCase} — e `painel` e
     * `evento` são duas <b>fatias</b>. A regra 3 da fronteira proíbe fatia conhecer fatia,
     * e a guarda teria reprovado o build, corretamente: saúde não é assunto do domínio de
     * eventos, é do armazenamento. O peer `persistencia` é o lugar certo.</p>
     */
    @Inject
    AgroalDataSource dataSource;

    @Inject
    Relogio relogio;

    @GET
    public Response verificar() {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("instante", relogio.agora().toString());

        try {
            // `esquema_migracao` e nao `SELECT 1`: o segundo provaria so que a conexao
            // abre. Contar a tabela de controle prova que A MIGRACAO RODOU — que e a
            // diferenca entre "o banco subiu" e "o banco esta pronto para uso". Um banco
            // vazio aceita conexao e responde 200 a `SELECT 1`.
            long quantos;
            try (Connection c = Conexoes.abrir(dataSource, "esquema_migracao");
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT count(*) FROM esquema_migracao");
                 ResultSet rs = ps.executeQuery()) {
                quantos = rs.next() ? rs.getLong(1) : 0L;
            }
            if (quantos == 0) {
                // Zero migracao registrada e banco NAO PRONTO, nao banco saudavel vazio.
                // Excecao PROPRIA, nao generica: a catraca do projeto proibe generica,
                // e com razao — "banco vazio" e "banco fora" mandam investigar lugares
                // opostos, e so o tipo separa os dois.
                throw new org.nasa.persistencia.domain.exceptions
                        .EsquemaNaoAplicadoException("esquema_migracao");
            }
            corpo.put("estado", "OK");
            corpo.put("banco", "responde");
            // A CONTAGEM NAO ENTRA NA RESPOSTA. Este endereco e publico e sem
            // autenticacao: volume de dado e informacao de negocio, e monitor nenhum
            // precisa dela. Ela vai para o log, que tem dono.
            LOG.debug(Registro.de(OPERACAO, "banco", "ok, " + quantos + " migracao(oes)"));
            return Response.ok(corpo).build();

        } catch (java.sql.SQLException | RuntimeException bancoFora) {
            // 503, e nao 200 com um campo. Monitor le STATUS; muitos nem olham o corpo.
            corpo.put("estado", "FORA");
            corpo.put("banco", "nao responde");
            LOG.error(Registro.recusa(OPERACAO, "banco", "NAO_RESPONDE"), bancoFora);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(corpo).build();
        }
    }
}
