package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Papel;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FamiliaApiTest extends IntegracaoBase {

    @Test
    void consultaEAtualizaAFamilia() throws Exception {
        mvc.perform(get("/api/familia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planoEmVigor.codigo").value("PREMIUM"))
                .andExpect(jsonPath("$.planoEmVigor.recursos[0]").value("INVESTIMENTOS"))
                .andExpect(jsonPath("$.membros[0].papel").value("DONO"));
        mvc.perform(put("/api/familia").contentType(APPLICATION_JSON)
                        .content("{\"nome\": \"Família Souza\", \"metaAportePercentual\": 20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Família Souza"))
                .andExpect(jsonPath("$.metaAportePercentual").value(20));
        mvc.perform(put("/api/familia").contentType(APPLICATION_JSON)
                        .content("{\"nome\": \"X\", \"metaAportePercentual\": 120}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void estimativaDosProximosMeses() throws Exception {
        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.estimativa").value("MEDIA"));
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"MAXIMO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativa").value("MAXIMO"));
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"TUDO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // Mudar o nome não mexe na estimativa.
        mvc.perform(put("/api/familia").contentType(APPLICATION_JSON).content("{\"nome\": \"Família Souza\"}"))
                .andExpect(jsonPath("$.estimativa").value("MAXIMO"));
    }

    @Test
    void membroConsultaMasNaoAltera() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        var como = mvcComo(filha);
        como.perform(get("/api/familia")).andExpect(status().isOk());
        como.perform(put("/api/familia").contentType(APPLICATION_JSON).content("{\"nome\": \"Minha\"}"))
                .andExpect(status().isForbidden());
        como.perform(delete("/api/familia/membros/{id}", dono.id())).andExpect(status().isForbidden());
        como.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"NENHUMA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void papelERemocaoDeMembros() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);

        mvc.perform(put("/api/familia/membros/{id}/papel", dono.id()).contentType(APPLICATION_JSON)
                        .content("{\"papel\": \"MEMBRO\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/familia/membros/{id}/papel", filha.id()).contentType(APPLICATION_JSON)
                        .content("{\"papel\": \"DONO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("DONO"));
        mvc.perform(delete("/api/familia/membros/{id}", dono.id())).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/familia/membros/{id}", filha.id())).andExpect(status().isNoContent());
        mvc.perform(get("/api/familia"))
                .andExpect(jsonPath("$.membros[?(@.email == 'filha@teste.com')].ativo").value(false));

        // Membro de outra família: 404.
        Membro outro = criarFamilia("outro@teste.com", Plano.PREMIUM);
        mvc.perform(delete("/api/familia/membros/{id}", outro.id())).andExpect(status().isNotFound());
    }

    @Test
    void adminDaPlataformaAjustaAssinatura() throws Exception {
        mvc.perform(get("/api/admin/familias")).andExpect(status().isForbidden());

        Usuario admin = criarUsuario("admin@teste.com", Papel.ADMIN);
        Membro adminNaFamilia = membros.salvar(Membro.novo(criarFamilia("fam-admin@teste.com", Plano.PREMIUM).familiaId(),
                admin.id(), PapelFamilia.MEMBRO));
        var como = mvcComo(adminNaFamilia);
        como.perform(get("/api/admin/familias")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dono.familiaId()));
        como.perform(put("/api/admin/familias/{id}/assinatura", dono.familiaId()).contentType(APPLICATION_JSON)
                        .content("{\"plano\": \"FAMILIA\", \"status\": \"ATIVA\", \"validaAte\": \"2026-10-16\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assinatura.plano").value("FAMILIA"))
                // Vencida ontem: vale o gratuito.
                .andExpect(jsonPath("$.planoEmVigor.codigo").value("GRATUITO"));
    }
}
