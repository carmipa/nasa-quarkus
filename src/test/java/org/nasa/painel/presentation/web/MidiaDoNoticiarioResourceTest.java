package org.nasa.painel.presentation.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Prova da trava de SSRF do proxy de imagens.
 *
 * <p><b>PROPÓSITO.</b> O carrossel da home busca as imagens do GDACS <b>pelo nosso
 * servidor</b>, por duas razões medidas: o gdacs.org limita vazão (a mesma URL responde
 * {@code 200} isolada e falha em sequência), e um {@code <img>} apontando para fora
 * entregaria o IP de cada visitante a um terceiro.</p>
 *
 * <p><b>MAS TODO PROXY É UM CONVITE A SSRF.</b> Um endpoint que busca a URL que lhe
 * mandarem transforma o nosso servidor em ferramenta: varredura da rede interna, leitura
 * de endpoints de metadados de nuvem, acesso a serviços que só existem atrás do firewall.
 * Cada teste abaixo é um <b>controle positivo</b> — um ataque conhecido que precisa ser
 * recusado. Sem eles, a trava seria esperança, e não trava.</p>
 *
 * <p><b>O caso {@code gdacs.org.atacante.com} é o mais importante.</b> Ele passa por
 * qualquer verificação escrita como {@code host.endsWith("gdacs.org")} — que é como quase
 * todo mundo escreve este filtro na primeira tentativa. Por isso a comparação aqui é de
 * <b>igualdade exata</b>, e este teste existe para que ela continue sendo.</p>
 */
@DisplayName("proxy de midia — a trava de SSRF, com os ataques conhecidos")
class MidiaDoNoticiarioResourceTest {

    @Test
    @DisplayName("a URL legitima do GDACS passa — sem isto os testes abaixo seriam vacuos")
    void urlLegitimaPassa() {
        // CONTROLE do controle: uma trava que recusa TUDO passaria em todos os casos
        // negativos e seria inútil. Este caso prova que ela deixa passar o que deve.
        var ok = MidiaDoNoticiarioResource.validar(
                "https://www.gdacs.org/contentdata/resources/imgtemp/gdacs/eq/eq1730728_1.png");
        assertNotNull(ok, "a URL real do GDACS foi recusada: a trava esta apertada demais");
        assertEquals("www.gdacs.org", ok.getHost());
    }

    @Test
    @DisplayName("O ATAQUE CLASSICO: `gdacs.org.atacante.com` — quebra `endsWith`")
    void sufixoDisfarcadoEhRecusado() {
        // Este dominio TERMINA em "gdacs.org" e pertence a quem quiser registra-lo.
        // Um filtro escrito com endsWith(".gdacs.org") ou contains("gdacs.org") o
        // aprovaria — e e assim que quase todo mundo escreve na primeira tentativa.
        assertNull(MidiaDoNoticiarioResource.validar("https://gdacs.org.atacante.com/x.png"));
        assertNull(MidiaDoNoticiarioResource.validar("https://www.gdacs.org.evil.net/x.png"));
        assertNull(MidiaDoNoticiarioResource.validar("https://naowww.gdacs.org/x.png"));
    }

    @Test
    @DisplayName("METADADOS DE NUVEM: 169.254.169.254 e recusado")
    void metadadosDeNuvemSaoRecusados() {
        // O alvo numero um de SSRF: o endereco de metadados devolve credenciais de
        // maquina em varios provedores, e so e alcancavel DE DENTRO — que e exatamente
        // o que um proxy oferece de graca.
        assertNull(MidiaDoNoticiarioResource.validar("https://169.254.169.254/latest/meta-data/"));
        assertNull(MidiaDoNoticiarioResource.validar("http://169.254.169.254/"));
    }

    @Test
    @DisplayName("REDE INTERNA e localhost sao recusados")
    void redeInternaEhRecusada() {
        for (String interno : new String[] {
                "https://localhost/x.png", "https://127.0.0.1/x.png",
                "https://10.0.0.1/x.png", "https://192.168.0.6/x.png",
                "https://172.16.0.1/x.png", "https://[::1]/x.png" }) {
            assertNull(MidiaDoNoticiarioResource.validar(interno), "aprovou: " + interno);
        }
    }

    @Test
    @DisplayName("ESQUEMAS que nao sao https sao recusados")
    void esquemasPerigososSaoRecusados() {
        // `file://` le o disco do servidor; `gopher://` foi historicamente usado para
        // falar com Redis e SMTP a partir de um proxy.
        assertNull(MidiaDoNoticiarioResource.validar("file:///etc/passwd"));
        assertNull(MidiaDoNoticiarioResource.validar("file:///C:/Windows/win.ini"));
        assertNull(MidiaDoNoticiarioResource.validar("gopher://www.gdacs.org/x"));
        assertNull(MidiaDoNoticiarioResource.validar("ftp://www.gdacs.org/x.png"));
        // http simples tambem sai: sem TLS, a resposta pode ser trocada no caminho.
        assertNull(MidiaDoNoticiarioResource.validar("http://www.gdacs.org/x.png"));
    }

    @Test
    @DisplayName("`usuario:senha@` no meio da URL e recusado")
    void userinfoEhRecusado() {
        // E como se disfarca o host verdadeiro: alguns analisadores leem
        // "https://www.gdacs.org@evil.com/" como host `evil.com`, e outros como
        // `www.gdacs.org`. Recusar userinfo tira a ambiguidade da mesa.
        assertNull(MidiaDoNoticiarioResource.validar("https://user:pass@www.gdacs.org/x.png"));   // SEGREDO-FALSO-POSITIVO-AUTORIZADO: credencial INVENTADA, e este teste prova que a URL e RECUSADA
        assertNull(MidiaDoNoticiarioResource.validar("https://www.gdacs.org@evil.com/x.png"));
    }

    @Test
    @DisplayName("PORTA diferente de 443 e recusada")
    void portaEstranhaEhRecusada() {
        // Mesmo no host certo: uma porta alta pode ser um servico interno exposto por
        // engano naquela maquina.
        assertNull(MidiaDoNoticiarioResource.validar("https://www.gdacs.org:8080/x.png"));
        assertNull(MidiaDoNoticiarioResource.validar("https://www.gdacs.org:22/x.png"));
        // 443 explicito e a porta padrao: passa.
        assertNotNull(MidiaDoNoticiarioResource.validar("https://www.gdacs.org:443/x.png"));
    }

    @Test
    @DisplayName("entrada vazia, nula ou absurdamente longa e recusada")
    void entradaTortaEhRecusada() {
        assertNull(MidiaDoNoticiarioResource.validar(null));
        assertNull(MidiaDoNoticiarioResource.validar(""));
        assertNull(MidiaDoNoticiarioResource.validar("   "));
        assertNull(MidiaDoNoticiarioResource.validar("nao e uma uri"));
        // URL gigante: teto para nao virar vetor de consumo de memoria.
        assertNull(MidiaDoNoticiarioResource.validar(
                "https://www.gdacs.org/" + "a".repeat(600) + ".png"));
    }
}
