package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ano e ordem fora da faixa são recusados com 400 e o motivo, em vez de chegar à regra de negócio. */
class FaixasApiTest extends IntegracaoBase {

    @Test
    void anoForaDaFaixaNaUrlERecusado() throws Exception {
        for (String ano : new String[]{"1999", "2101", "-5", "99999999"}) {
            mvc.perform(get("/api/visao-anual/" + ano)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(containsString("ano: deve estar entre 2000 e 2100")));
            mvc.perform(get("/api/saldos").param("ano", ano)).andExpect(status().isBadRequest());
            mvc.perform(get("/api/investimentos/carteira").param("ano", ano)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void anoNosLimitesPassaPelaFaixa() throws Exception {
        mvc.perform(get("/api/saldos").param("ano", "2026")).andExpect(status().isOk());
        // 2000 e 2100 estão na faixa; quem recusa é a regra de negócio (fora dos anos com dados), não a faixa (400).
        for (String ano : new String[]{"2000", "2100"}) {
            mvc.perform(get("/api/visao-anual/" + ano)).andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void anoForaDaFaixaNoCorpoDoImpactoERecusado() throws Exception {
        mvc.perform(post("/api/impacto").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 15, \"periodicidade\": \"DIAS_UTEIS\", \"ano\": 99999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("ano: deve estar entre 2000 e 2100")));
    }

    @Test
    void ordemNegativaERecusada() throws Exception {
        mvc.perform(post("/api/categorias/grupos").contentType(APPLICATION_JSON).content("""
                        {"tipo": "DESPESA", "natureza": "VARIAVEL", "nome": "Pets", "ordem": -1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("ordem: não pode ser negativa")));
        mvc.perform(post("/api/categorias/grupos").contentType(APPLICATION_JSON).content("""
                        {"tipo": "DESPESA", "natureza": "VARIAVEL", "nome": "Pets", "ordem": 0}"""))
                .andExpect(status().isCreated());
    }
}
