package org.nasa.endereco.infrastructure.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.endereco.domain.Cep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da interpretação das respostas — <b>sem rede</b>, pela costura dos adaptadores.
 *
 * <p><b>PROPÓSITO.</b> Estes testes existem para os dois casos que passariam despercebidos
 * em produção: a resposta da BrasilAPI <b>sem</b> {@code location} (1 de cada 6 CEPs
 * medidos) e a resposta de erro do ViaCEP, que vem com <b>HTTP 200</b>. Nenhum dos dois
 * produz erro; os dois produzem dado errado no banco.</p>
 *
 * <p>As respostas coladas aqui são as <b>reais</b>, medidas em 2026-09-02 — não inventadas.
 * Usar corpo imaginário provaria que o código lê o que eu imaginei, não o que o provedor
 * manda.</p>
 */
@DisplayName("adaptadores de CEP — as duas respostas que enganam")
class AdaptadoresDeCepTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------- BrasilAPI

    /** Resposta REAL do CEP 01310200, medida em 02/09. */
    private static final String BRASILAPI_COM_COORDENADA = """
            {"cep":"01310200","state":"SP","city":"São Paulo","neighborhood":"Bela Vista",
             "street":"Avenida Paulista","service":"open-cep",
             "location":{"type":"Point","coordinates":{"longitude":"-46.6559677","latitude":"-23.5614961"}}}""";

    /** Resposta REAL do CEP 69900000: veio pelo provedor `correios`, SEM coordenada. */
    private static final String BRASILAPI_SEM_COORDENADA = """
            {"cep":"69900000","state":"AC","city":"","neighborhood":"","street":"",
             "service":"correios","location":{"type":"Point","coordinates":{}}}""";

    private static BrasilApiCepAdapter brasilApi() {
        var a = new BrasilApiCepAdapter();
        a.json = JSON;
        return a;
    }

    @Test
    @DisplayName("BrasilAPI COM location: le a coordenada, que vem como TEXTO")
    void brasilApiComCoordenada() {
        var lido = brasilApi().interpretar(new Cep("01310200"), BRASILAPI_COM_COORDENADA);

        assertTrue(lido.coordenada().isPresent(), "a coordenada estava na resposta");
        assertEquals(-23.5614961, lido.coordenada().get().latitude(), 0.0000001);
        assertEquals(-46.6559677, lido.coordenada().get().longitude(), 0.0000001);
        assertEquals("Avenida Paulista", lido.logradouro());
        assertEquals("SP", lido.uf());
        System.out.println("[CEP] brasilapi com coordenada: " + lido.coordenada().get());
    }

    @Test
    @DisplayName("BrasilAPI SEM location: devolve AUSENCIA, e nunca (0,0)")
    void brasilApiSemCoordenada() {
        // Este e o caso medido: 1 de 6 CEPs. Preencher com 0,0 aqui poria o endereco no
        // Golfo da Guine, o mapa desenharia o pino la, e nenhum erro apareceria.
        var lido = brasilApi().interpretar(new Cep("69900000"), BRASILAPI_SEM_COORDENADA);

        assertFalse(lido.coordenada().isPresent(),
                "coordenada ausente tem de ser AUSENCIA — o null island nao e um endereco");
    }

    @Test
    @DisplayName("BrasilAPI com corpo ilegivel: excecao PROPRIA, nao 'provedor fora'")
    void brasilApiCorpoIlegivel() {
        // O provedor esta no ar, responde 200, e mudou o formato. Sem exceção própria
        // isto viraria "indisponivel" e ninguem iria olhar o contrato.
        var erro = assertThrows(RespostaDeProvedorIlegivelException.class,
                () -> brasilApi().interpretar(new Cep("01310200"), "{ isto nao e json"));
        System.out.println("[CEP] " + erro.linhaDeLog());
        assertTrue(erro.getMessage().contains("contrato pode ter mudado"));
    }

    // ---------------------------------------------------------------- ViaCEP

    /** Resposta REAL do ViaCEP para 01310200 — note a AUSENCIA de lat/lon. */
    private static final String VIACEP_OK = """
            {"cep":"01310-200","logradouro":"Avenida Paulista","complemento":"de 1512 a 2132 - lado par",
             "bairro":"Bela Vista","localidade":"São Paulo","uf":"SP","estado":"São Paulo",
             "regiao":"Sudeste","ibge":"3550308","gia":"1004","ddd":"11","siafi":"7107"}""";

    /** A ARMADILHA: CEP inexistente responde HTTP 200 com este corpo. */
    private static final String VIACEP_ERRO = """
            {"erro": "true"}""";

    private static ViaCepAdapter viaCep() {
        var a = new ViaCepAdapter();
        a.json = JSON;
        return a;
    }

    @Test
    @DisplayName("ViaCEP OK: le o endereco — e NUNCA traz coordenada, porque ele nao tem")
    void viaCepOk() {
        var lido = viaCep().interpretar(new Cep("01310200"), VIACEP_OK);

        assertTrue(lido.isPresent());
        assertEquals("Avenida Paulista", lido.get().logradouro());
        assertFalse(lido.get().coordenada().isPresent(),
                "era exatamente por isso que o legado precisava de uma segunda chamada");
    }

    @Test
    @DisplayName("A ARMADILHA do ViaCEP: erro vem com HTTP 200 e corpo {erro:true}")
    void viaCepErroVemComDuzentos() {
        // Quem confere so o status HTTP le o corpo de erro como se fosse endereco, e
        // grava um registro com TODOS os campos vazios — sem erro nenhum aparecer.
        var lido = viaCep().interpretar(new Cep("99999999"), VIACEP_ERRO);

        assertTrue(lido.isEmpty(),
                "corpo de erro com status 200 tem de virar AUSENCIA, nao endereco vazio");
    }
}
