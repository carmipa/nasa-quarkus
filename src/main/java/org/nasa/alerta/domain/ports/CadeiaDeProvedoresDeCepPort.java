package org.nasa.alerta.domain.ports;

import java.util.List;

/**
 * A ordem em que os provedores de CEP são tentados.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Qual provedor vem primeiro é decisão de
 * <b>infraestrutura</b> — muda com preço, latência e disponibilidade, e não com regra de
 * negócio. O caso de uso precisa saber que existe uma ordem; não precisa saber qual.</p>
 *
 * <p><b>POR QUE ESTA PORTA EXISTE.</b> A primeira versão do caso de uso injetava os dois
 * adaptadores direto para garantir a ordem — e a guarda de fronteira <b>reprovou o
 * build</b>, corretamente: {@code application} não depende de {@code infrastructure}. A
 * consequência prática de ter cedido seria perder a testabilidade sem rede, que é
 * justamente o que a regra compra. A porta resolve os dois lados: a ordem continua
 * declarada, e o caso de uso continua rodando com dublê.</p>
 *
 * <p><b>INVARIANTE.</b> A ordem é <b>declarada</b>, nunca deduzida da resolução de beans
 * do CDI — que não é garantida e faria o provedor primário virar reserva sem ninguém
 * tocar em nada, trocando 0,23 s por 1,04 s em toda consulta, silenciosamente.</p>
 *
 * <p><b>FALHA.</b> Lista vazia é defeito de configuração, e o caso de uso a trata como
 * indisponibilidade total — nunca como "o CEP não existe".</p>
 */
public interface CadeiaDeProvedoresDeCepPort {

    /** Do primeiro a ser tentado ao último. */
    List<ConsultaCepPort> emOrdem();
}
