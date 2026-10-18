package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApagarDadosApiTest extends IntegracaoBase {

    private static final String URL = "/api/familia/dados/apagamento";

    private void preencherOrcamento() throws Exception {
        lancar("Habitação", "Supermercado", "Mercado", "300", "2026-10-05");
        postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Internet", "valor": 99.90, "dia": 10, "frequencia": "MENSAL",
                 "inicio": "2026-01"}""".formatted(categoria("Habitação", "Internet")));
        mvc.perform(put("/api/saldos/2026-01").contentType(APPLICATION_JSON).content("{\"valor\": 5000}"))
                .andExpect(status().isOk());
        Long cdb = postarERetornarId("/api/investimentos", "{\"nome\": \"CDB\", \"tipo\": \"RENDA_FIXA\"}");
        mvc.perform(put("/api/investimentos/{id}/movimentos/2026-01", cdb).contentType(APPLICATION_JSON)
                        .content("{\"aporte\": 1000}"))
                .andExpect(status().isOk());
        postarERetornarId("/api/categorias/grupos", "{\"tipo\": \"DESPESA\", \"natureza\": \"FIXA\", \"nome\": \"Pets\"}");
    }

    private int contar(String tabela, Membro daFamilia) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where familia_id = ?", Integer.class,
                daFamilia.familiaId());
    }

    @Test
    void apagaOOrcamentoERecriaAsCategoriasPadrao() throws Exception {
        int gruposPadrao = contar("grupo_categoria", dono);
        int categoriasPadrao = contar("categoria", dono);
        preencherOrcamento();

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isNoContent());

        for (String tabela : new String[]{"lancamento", "recorrencia", "saldo_mes", "investimento"}) {
            assertThat(contar(tabela, dono)).as(tabela).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from movimento_investimento", Integer.class)).isZero();
        assertThat(contar("grupo_categoria", dono)).isEqualTo(gruposPadrao);
        assertThat(contar("categoria", dono)).isEqualTo(categoriasPadrao);
        assertThat(jdbc.queryForObject("select count(*) from grupo_categoria where familia_id = ? and nome = 'Pets'",
                Integer.class, dono.familiaId())).isZero();
        // Pessoas e família ficam; dá para lançar de novo na hora.
        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.membros[0].papel").value("DONO"));
        lancar("Habitação", "Supermercado", "Mercado", "120", "2026-10-06");
    }

    @Test
    void naoMexeEmOutraFamilia() throws Exception {
        Membro outro = criarFamilia("outro@teste.com", Plano.PREMIUM);
        var comoOutro = mvcComo(outro);
        postarERetornarId(comoOutro, "/api/lancamentos", """
                {"categoriaId": %d, "descricao": "Mercado", "valor": 50, "data": "2026-10-05"}"""
                .formatted(categoria(outro, "Habitação", "Supermercado")));
        int categoriasDoOutro = contar("categoria", outro);

        mvc.perform(post(URL).contentType(APPLICATION_JSON).content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isNoContent());

        assertThat(contar("lancamento", outro)).isEqualTo(1);
        assertThat(contar("categoria", outro)).isEqualTo(categoriasDoOutro);
    }

    @Test
    void exigeASenhaEContaAsTentativas() throws Exception {
        preencherOrcamento();
        mvc.perform(post(URL).contentType(APPLICATION_JSON).content("{\"senha\": \"senha-errada-123\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(URL).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        assertThat(contar("lancamento", dono)).isEqualTo(1);

        for (int i = 0; i < 4; i++) {
            mvc.perform(post(URL).contentType(APPLICATION_JSON).content("{\"senha\": \"senha-errada-123\"}"));
        }
        mvc.perform(post(URL).contentType(APPLICATION_JSON).content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isTooManyRequests());
        assertThat(contar("lancamento", dono)).isEqualTo(1);
    }

    @Test
    void soQuemOrganizaApaga() throws Exception {
        preencherOrcamento();
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        mvcComo(filha).perform(post(URL).contentType(APPLICATION_JSON)
                        .content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().isForbidden());
        mvcSemUsuario.perform(post(URL).contentType(APPLICATION_JSON)
                        .content("{\"senha\": \"%s\"}".formatted(SENHA_PADRAO)))
                .andExpect(status().is4xxClientError());
        assertThat(contar("lancamento", dono)).isEqualTo(1);
    }
}
