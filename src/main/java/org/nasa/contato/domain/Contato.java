package org.nasa.contato.domain;

import org.nasa.contato.domain.exceptions.ContatoInvalidoException;

import java.time.Instant;
import java.util.Optional;

/**
 * Como se fala com uma pessoa.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o destino do alerta. Quando um evento natural
 * acontece perto de um endereço cadastrado, é daqui que sai para quem avisar. Um contato
 * cadastrado errado não produz erro nenhum — produz <b>silêncio</b> na hora do desastre,
 * que é a falha mais cara deste sistema e a que ninguém percebe até acontecer.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O e-mail é obrigatório; os telefones não.</b> Ele é o único canal que o
 *       sistema sabe usar hoje. Aceitar um contato só com telefone criaria um registro
 *       que parece completo e por onde nada será enviado.</li>
 *   <li><b>Telefone guarda só dígitos.</b> "(11) 98765-4321" e "11987654321" são o mesmo
 *       número; guardar como digitado faria o mesmo telefone existir em quatro formas, e
 *       nenhuma busca encontraria as outras três. É a mesma cicatriz do documento do
 *       cliente, onde "111.222.333-44" e "11122233344" eram duas pessoas.</li>
 *   <li><b>DDD, quando informado, tem exatamente dois dígitos.</b> DDD com três dígitos
 *       é quase sempre o número colado no campo errado — e o resultado é uma ligação que
 *       nunca completa.</li>
 *   <li><b>Telefone, quando informado, tem 8 ou 9 dígitos.</b> Oito é fixo, nove é
 *       celular. Menos que isso é campo pela metade; mais é DDD grudado.</li>
 *   <li><b>Campo opcional vazio é AUSENTE, nunca string vazia.</b> {@code Optional} torna
 *       impossível confundir "não tem WhatsApp" com "tem WhatsApp em branco" — o segundo
 *       apareceria na tela como um número por preencher que ninguém preencheu.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link ContatoInvalidoException} com o
 * <b>nome do campo</b> no alvo, para a tela destacar a caixa certa e o log falar do mesmo
 * campo. O valor digitado não entra na mensagem: é dado pessoal.</p>
 *
 * @param id        nulo enquanto não gravado
 * @param ddd       dois dígitos, ou nulo
 * @param telefone  fixo, só dígitos, ou nulo
 * @param celular   celular, só dígitos, ou nulo
 * @param whatsapp  número de WhatsApp, só dígitos, ou nulo
 * @param email     obrigatório: o único canal garantido
 * @param tipo      para que serve este contato; decide quem recebe alerta
 * @param criadoEm  instante UTC da gravação; nulo enquanto não gravado
 */
public record Contato(Long id, String ddd, String telefone, String celular, String whatsapp,
                      Email email, TipoContato tipo, Instant criadoEm) {

    public Contato {
        if (email == null) {
            throw new ContatoInvalidoException("email", "ausente — e o unico canal garantido");
        }
        if (tipo == null) {
            throw new ContatoInvalidoException("tipoContato", "ausente");
        }
        ddd = digitosOuNulo(ddd, "ddd", 2, 2);
        telefone = digitosOuNulo(telefone, "telefone", 8, 9);
        celular = digitosOuNulo(celular, "celular", 8, 9);
        whatsapp = digitosOuNulo(whatsapp, "whatsapp", 8, 9);
    }

    /**
     * Normaliza um campo opcional de telefone.
     *
     * <p>Em branco vira {@code null} — porque "não informado" e "informado vazio" não
     * podem ser estados diferentes de uma mesma ausência. Informado, guarda só dígitos e
     * exige o tamanho.</p>
     */
    private static String digitosOuNulo(String valor, String campo, int minimo, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String so = valor.replaceAll("\\D", "");
        if (so.isEmpty()) {
            // Digitaram algo que não tem dígito nenhum: "(  )", "não tem". É ausência
            // com aparência de preenchimento, e vira ausência de verdade.
            return null;
        }
        if (so.length() < minimo || so.length() > maximo) {
            // REVISÃO DE ERRO DE BOA-FÉ: 10 ou 11 dígitos num campo de telefone é, quase
            // sempre, o número colado COM o DDD — porque é assim que telefone se escreve
            // no Brasil, e a pessoa não errou nada, só ignorou que há um campo separado.
            // Responder "esperado 8 ou 9 dígitos, recebi 10" é tecnicamente correto e
            // não ajuda: manda contar dígitos em vez de mostrar o que fazer.
            String pista = (so.length() == 10 || so.length() == 11)
                    ? " — parece o numero com o DDD junto; o DDD tem campo proprio ao lado"
                    : "";
            throw new ContatoInvalidoException(campo,
                    "esperado " + (minimo == maximo ? minimo + " digitos"
                            : minimo + " ou " + maximo + " digitos") + ", recebi "
                            + so.length() + pista);
        }
        return so;
    }

    /** Novo cadastro: id e instante vêm do repositório, no momento da gravação. */
    public static Contato novo(String ddd, String telefone, String celular, String whatsapp,
                               Email email, TipoContato tipo) {
        return new Contato(null, ddd, telefone, celular, whatsapp, email, tipo, null);
    }

    /** O mesmo contato, com os dados alterados. */
    public Contato com(String ddd, String telefone, String celular, String whatsapp,
                       Email email, TipoContato tipo) {
        return new Contato(this.id, ddd, telefone, celular, whatsapp, email, tipo, this.criadoEm);
    }

    public Optional<String> dddOpcional() {
        return Optional.ofNullable(ddd);
    }

    public Optional<String> telefoneOpcional() {
        return Optional.ofNullable(telefone);
    }

    public Optional<String> celularOpcional() {
        return Optional.ofNullable(celular);
    }

    public Optional<String> whatsappOpcional() {
        return Optional.ofNullable(whatsapp);
    }

    /**
     * Se este contato recebe alerta de desastre.
     *
     * <p>A tela mostra isto em voz alta: um contato que <b>não</b> recebe alerta é
     * legítimo e comum, mas quem cadastra precisa saber disso na hora — descobrir depois
     * do desastre é tarde.</p>
     */
    public boolean recebeAlerta() {
        return tipo.recebeAlerta();
    }

    /** O telefone preferido para exibição: celular, senão fixo. */
    public Optional<String> telefonePreferido() {
        return celular != null ? Optional.of(celular) : Optional.ofNullable(telefone);
    }

    /** Como a tela escreve o número, com o DDD quando existe. */
    public String contatoTelefonicoFormatado() {
        return telefonePreferido()
                .map(n -> (ddd == null ? "" : "(" + ddd + ") ") + formatarNumero(n))
                .orElse("—");
    }

    private static String formatarNumero(String digitos) {
        if (digitos.length() == 9) {
            return digitos.substring(0, 5) + "-" + digitos.substring(5);
        }
        if (digitos.length() == 8) {
            return digitos.substring(0, 4) + "-" + digitos.substring(4);
        }
        return digitos;
    }
}
