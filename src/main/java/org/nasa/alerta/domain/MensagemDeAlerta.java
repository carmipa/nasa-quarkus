package org.nasa.alerta.domain;

import org.nasa.geo.domain.Coordenada;

import java.time.Instant;
import java.util.List;

/**
 * O e-mail de alerta que a pessoa <b>receberia</b> — montado na hora, e nunca guardado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o produto da tela. Alguém informa o e-mail e o CEP, e vê
 * imediatamente a mensagem que o sistema mandaria: quais desastres a NASA publicou perto
 * dali, a que distância, e há quanto tempo.</p>
 *
 * <p><b>POR QUE NADA É GUARDADO — a decisão que define esta fatia.</b> A versão anterior
 * cadastrava inscritos: nome, e-mail, telefone e CEP numa tabela. Isso cria três problemas
 * que não existem quando não se guarda nada:</p>
 * <ol>
 *   <li><b>uma lista de e-mails é uma lista de e-mails.</b> Ela vaza, é pedida por lei, é
 *       alvo. Não guardar é a única proteção que não pode falhar;</li>
 *   <li><b>dado pessoal traz obrigação</b> — consentimento, retenção, exclusão a pedido.
 *       Obrigação que este projeto não tem estrutura para cumprir de verdade;</li>
 *   <li><b>o cadastro era um formulário público que escrevia no banco</b>, e formulário
 *       assim é abusado. A proteção virou desnecessária: não há o que encher.</li>
 * </ol>
 *
 * <p>E há um ganho que não era o objetivo: os datasets exportados deste sistema nascem
 * <b>sanitizados</b>, porque não existe dado pessoal para sanitizar.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O e-mail informado NÃO entra no corpo da mensagem nem em log.</b> Ele serve para
 *       a pessoa ver o alerta endereçado a ela na tela, e some no fim da requisição. Pôr um
 *       endereço de e-mail dentro de um texto que alguém pode copiar, imprimir ou
 *       compartilhar é vazá-lo por outro caminho.</li>
 *   <li><b>Zero desastre por perto é RESULTADO, não erro.</b> É a resposta mais comum e a
 *       melhor notícia possível — e a mensagem diz isso com todas as letras, em vez de
 *       mostrar uma lista vazia que parece falha de carregamento.</li>
 *   <li><b>A distância é a GEODÉSICA</b>, medida sobre a curvatura da Terra. O canto de uma
 *       caixa de 100 km fica a 141 km, e um evento ali NÃO está no raio pedido.</li>
 * </ol>
 *
 * @param assunto     a linha de assunto do e-mail
 * @param saudacao    como a mensagem começa
 * @param corpo       os parágrafos, na ordem
 * @param desastres   o que foi encontrado, do mais próximo ao mais distante
 * @param ondeVoceEsta o CEP e a coordenada usados — a pessoa precisa poder conferir se o
 *                    sistema entendeu o lugar certo antes de confiar no resultado
 * @param raioKm      o raio pedido
 * @param montadaEm   quando, em UTC. A mensagem tem validade: um alerta de ontem sobre
 *                    evento que já foi encerrado engana
 */
public record MensagemDeAlerta(String assunto, String saudacao, List<String> corpo,
                               List<DesastreProximo> desastres, Local ondeVoceEsta,
                               double raioKm, Instant montadaEm) {

    /**
     * O lugar de onde a busca partiu.
     *
     * @param cep         o CEP informado — identifica uma região, não uma pessoa
     * @param descricao   o endereço que o provedor devolveu, para conferência
     * @param coordenada  a posição usada no cálculo
     */
    public record Local(String cep, String descricao, Coordenada coordenada) {
    }

    /** Se não achou nada. É a resposta mais comum, e a melhor. */
    public boolean semDesastres() {
        return desastres.isEmpty();
    }

    /** O mais próximo, para o assunto e para o destaque da tela. */
    public DesastreProximo maisProximo() {
        return desastres.isEmpty() ? null : desastres.get(0);
    }
}
