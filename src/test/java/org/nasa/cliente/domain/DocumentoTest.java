package org.nasa.cliente.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.cliente.domain.exceptions.DocumentoInvalidoException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prova da normalização — o defeito do legado que fazia a unicidade proteger nada.
 *
 * <p><b>PROPÓSITO.</b> No legado o documento era texto livre guardado como digitado.
 * {@code "111.222.333-44"} e {@code "11122233344"} eram <b>duas pessoas</b>: o mesmo CPF
 * entrava duas vezes, cada cadastro com endereços diferentes, e o alerta ia para metade
 * deles. O teste que importa aqui é o que prova que as duas formas agora colidem.</p>
 */
@DisplayName("Documento — a normalizacao que faz a unicidade funcionar")
class DocumentoTest {

    @Test
    @DisplayName("O DEFEITO DO LEGADO: as duas formas do MESMO CPF sao o MESMO documento")
    void duasFormasDoMesmoCpfSaoIguais() {
        var pontuado = new Documento("111.222.333-44");
        var limpo = new Documento("11122233344");
        var comEspacos = new Documento("  111 222 333 44 ");

        assertEquals(limpo, pontuado, "no legado estas duas eram pessoas diferentes");
        assertEquals(limpo, comEspacos);
        assertEquals("11122233344", pontuado.digitos(),
                "so os digitos sao guardados — e por isso que o UNIQUE do banco pega");
    }

    @Test
    @DisplayName("CPF diferente continua diferente — a normalizacao nao colapsa quem nao deve")
    void cpfsDiferentesContinuamDiferentes() {
        assertNotEquals(new Documento("111.222.333-44"), new Documento("111.222.333-45"));
    }

    @Test
    @DisplayName("aceita CPF (11 digitos) e CNPJ (14)")
    void aceitaCpfECnpj() {
        assertEquals(11, new Documento("111.222.333-44").digitos().length());
        assertEquals(14, new Documento("11.222.333/0001-44").digitos().length());
    }

    @Test
    @DisplayName("recusa tamanho que nao e de documento brasileiro")
    void recusaTamanhoInvalido() {
        var erro = assertThrows(DocumentoInvalidoException.class, () -> new Documento("123"));
        System.out.println("[DOC] " + erro.linhaDeLog());
        assertThrows(DocumentoInvalidoException.class, () -> new Documento("123456789012"));
        assertThrows(DocumentoInvalidoException.class, () -> new Documento(""));
        assertThrows(DocumentoInvalidoException.class, () -> new Documento(null));
    }

    @Test
    @DisplayName("texto sem digitos e recusado — 'a definir' nao pode virar identidade")
    void textoSemDigitosEhRecusado() {
        // Se passasse, dois cadastros com "a definir" seriam a MESMA pessoa aos olhos do
        // banco, e o terceiro seria recusado sem ninguem entender por que.
        assertThrows(DocumentoInvalidoException.class, () -> new Documento("a definir"));
    }

    @Test
    @DisplayName("formatado(): a forma que a pessoa reconhece, so para exibicao")
    void formatado() {
        assertEquals("111.222.333-44", new Documento("11122233344").formatado());
        assertEquals("11.222.333/0001-44", new Documento("11222333000144").formatado());
    }
}
