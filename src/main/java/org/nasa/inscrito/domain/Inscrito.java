package org.nasa.inscrito.domain;

import org.nasa.geo.domain.Coordenada;
import org.nasa.inscrito.domain.exceptions.InscricaoInvalidaException;

import java.time.Instant;
import java.util.Optional;

/**
 * Quem pediu para ser avisado quando houver desastre perto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a razão de o sistema existir. Alguém informa o nome, um
 * canal de aviso e onde está; quando a NASA publica um desastre dentro do raio, o alerta é
 * disparado para essa pessoa.</p>
 *
 * <p><b>O QUE ISTO SUBSTITUIU, e por quê.</b> Antes eram três cadastros separados —
 * cliente, contato e endereço —, com 5.645 linhas, três telas de CRUD e quatro tabelas de
 * ligação. Aquilo modelava um sistema de gestão de clientes; este sistema não gerencia
 * clientes, ele <b>avisa gente sobre desastre</b>. Uma inscrição é o que basta: quem é,
 * onde está, por onde avisar.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nome e e-mail são obrigatórios.</b> O e-mail é o único canal que o sistema sabe
 *       usar; aceitar inscrição sem ele criaria um registro que nunca pode ser avisado —
 *       um alerta que existe no banco e não chega a ninguém.</li>
 *   <li><b>O CEP é obrigatório, a COORDENADA não.</b> O CEP é o que a pessoa sabe informar;
 *       a coordenada é o que o sistema deriva dele, e provedores externos falham. Inscrição
 *       sem coordenada é <b>gravada e marcada</b>, não recusada: perder a inscrição porque
 *       o Nominatim estava fora seria punir a pessoa por uma falha nossa.</li>
 *   <li><b>Inscrição SEM coordenada não entra no cálculo de proximidade</b> — e isso é
 *       visível na tela, nunca silencioso. Ela existe, e o sistema diz que ainda não
 *       consegue avisá-la por proximidade.</li>
 *   <li><b>O telefone é opcional</b>, e hoje é só registro: o sistema não envia mensagem.
 *       Guardá-lo prometendo envio que não existe seria mentir no cadastro.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Campo obrigatório ausente lança
 * {@link InscricaoInvalidaException}, que nomeia o campo — nunca uma mensagem genérica que
 * obrigue a adivinhar qual dos cinco está errado.</p>
 *
 * @param id          nulo antes de gravar
 * @param nome        como a pessoa quer ser chamada no alerta
 * @param email       o canal de aviso; obrigatório
 * @param telefone    só dígitos, ou nulo. Registro, não canal — ainda
 * @param cep         onde a pessoa está; obrigatório
 * @param coordenada  derivada do CEP; <b>nula é estado legítimo</b> e significa "ainda não
 *                    dá para calcular proximidade para esta pessoa"
 * @param raioKm      a que distância ela quer ser avisada
 * @param criadoEm    quando se inscreveu, em UTC
 * @param canceladoEm quando cancelou; nulo significa ATIVA. Cancelamento não apaga: a
 *                    inscrição some dos alertas e o histórico do que já foi enviado
 *                    continua fazendo sentido
 */
public record Inscrito(Long id, String nome, Email email, String telefone, Cep cep,
                       Coordenada coordenada, double raioKm, Instant criadoEm,
                       Instant canceladoEm) {

    /**
     * Raio padrão, em quilômetros.
     *
     * <p>100 km é a distância em que um desastre natural ainda é assunto de quem mora ali —
     * fumaça de incêndio florestal viaja mais que isso, e uma tempestade severa a 100 km
     * chega em horas. É o mesmo padrão que a tela de proximidade já usava.</p>
     */
    public static final double RAIO_PADRAO_KM = 100.0;

    /** Menor raio aceito. Abaixo disso o alerta só dispara para quem está no evento. */
    public static final double RAIO_MINIMO_KM = 1.0;

    /**
     * Maior raio aceito.
     *
     * <p>20.000 km é metade da circunferência da Terra: acima disso o raio cobre o planeta
     * inteiro e "proximidade" deixa de significar coisa alguma. Recusar aqui é mais honesto
     * que aceitar e disparar tudo para todos.</p>
     */
    public static final double RAIO_MAXIMO_KM = 20_000.0;

    public Inscrito {
        nome = exigir(nome, "nome");
        if (email == null) {
            throw new InscricaoInvalidaException("email", "ausente");
        }
        if (cep == null) {
            throw new InscricaoInvalidaException("cep", "ausente");
        }
        if (raioKm < RAIO_MINIMO_KM || raioKm > RAIO_MAXIMO_KM) {
            throw new InscricaoInvalidaException("raioKm",
                    "fora de " + RAIO_MINIMO_KM + " a " + RAIO_MAXIMO_KM + " km");
        }
        // Telefone guarda so digitos: o mesmo numero digitado em quatro formatos viraria
        // quatro registros diferentes, e a busca nao acharia nenhum deles.
        telefone = (telefone == null || telefone.isBlank())
                ? null : telefone.replaceAll("\\D", "");
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new InscricaoInvalidaException(campo, "ausente");
        }
        return valor.trim();
    }

    /** Uma inscrição nova, ainda sem id e sem cancelamento. */
    public static Inscrito novo(String nome, Email email, String telefone, Cep cep,
                                Coordenada coordenada, double raioKm, Instant agora) {
        return new Inscrito(null, nome, email, telefone, cep, coordenada, raioKm, agora, null);
    }

    /**
     * Se esta inscrição pode receber alerta de proximidade.
     *
     * <p>Precisa das duas coisas: estar ativa <b>e</b> ter coordenada. Sem coordenada não há
     * o que comparar com a posição do desastre — e mandar alerta "por via das dúvidas" para
     * quem o sistema não sabe localizar seria pior que não mandar: treinaria a pessoa a
     * ignorar o aviso.</p>
     */
    public boolean recebeAlertaDeProximidade() {
        return canceladoEm == null && coordenada != null;
    }

    public boolean ativo() {
        return canceladoEm == null;
    }

    public Optional<Coordenada> coordenadaOpcional() {
        return Optional.ofNullable(coordenada);
    }

    public Optional<String> telefoneOpcional() {
        return Optional.ofNullable(telefone);
    }

    /** A mesma inscrição, com a coordenada que a geocodificação achou depois. */
    public Inscrito comCoordenada(Coordenada nova) {
        return new Inscrito(id, nome, email, telefone, cep, nova, raioKm, criadoEm, canceladoEm);
    }

    /** A mesma inscrição, cancelada. Não apaga: some dos alertas e preserva o histórico. */
    public Inscrito cancelada(Instant agora) {
        return new Inscrito(id, nome, email, telefone, cep, coordenada, raioKm, criadoEm, agora);
    }
}
