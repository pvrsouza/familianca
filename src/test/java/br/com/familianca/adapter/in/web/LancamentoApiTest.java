package br.com.familianca.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LancamentoApiTest extends IntegracaoBase {

    @Test
    void lancaListaEditaEExclui() throws Exception {
        Long mercado = lancar("Habitação", "Supermercado", "Compra do mês", "850.40", "2026-10-05");
        lancar("Receitas fixas", "Salário", "Salário", "9000", "2026-10-05");
        lancar("Habitação", "Supermercado", "Compra de 2025", "500", "2025-12-20");

        mvc.perform(get("/api/lancamentos/{id}", mercado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("DESPESA"))
                .andExpect(jsonPath("$.categoria.nome").value("Supermercado"))
                .andExpect(jsonPath("$.grupo.nome").value("Habitação"))
                .andExpect(jsonPath("$.membro.nome").value("Usuário dono"))
                .andExpect(jsonPath("$.competencia").value("2026-10"))
                .andExpect(jsonPath("$.valor").value(850.40));

        mvc.perform(get("/api/lancamentos")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/lancamentos").param("tipo", "RECEITA"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].descricao").value("Salário"));
        mvc.perform(get("/api/lancamentos").param("de", "2025-01-01").param("grupoId", grupo("Habitação").toString()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].descricao").value("Compra do mês"));
        mvc.perform(get("/api/lancamentos").param("de", "2026-12-01").param("ate", "2026-01-01"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/lancamentos/{id}", mercado).contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Feira", "valor": 120, "data": "2026-10-06"}"""
                        .formatted(categoria("Habitação", "Supermercado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Feira"));
        mvc.perform(delete("/api/lancamentos/{id}", mercado)).andExpect(status().isNoContent());
        mvc.perform(get("/api/lancamentos/{id}", mercado)).andExpect(status().isNotFound());
    }

    @Test
    void compraParcelada() throws Exception {
        String corpo = mvc.perform(post("/api/lancamentos/parcelados").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Geladeira", "valorTotal": 3000.10, "parcelas": 3,
                         "primeiraData": "2026-11-30"}""".formatted(categoria("Despesas temporárias e variáveis",
                        "Utilidades domésticas e decoração"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].valor").value(1000.04))
                .andExpect(jsonPath("$[1].valor").value(1000.03))
                .andExpect(jsonPath("$[1].data").value("2026-12-30"))
                .andExpect(jsonPath("$[2].data").value("2027-01-30"))
                .andExpect(jsonPath("$[2].parcela.numero").value(3))
                .andExpect(jsonPath("$[2].parcela.total").value(3))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(corpo, "$[*].id");

        mvc.perform(delete("/api/lancamentos/{id}", ids.get(1)).param("todasAsParcelas", "true"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from lancamento", Integer.class)).isZero();
    }

    @Test
    void membroLancaEAlteraSoOsProprios() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        var comoFilha = mvcComo(filha);
        Long cabeleireiro = categoria("Despesas pessoais", "Cabeleireiro");

        Long daFilha = postarERetornarId(comoFilha, "/api/lancamentos", """
                {"categoriaId": %d, "descricao": "Corte", "valor": 80, "data": "2026-10-10"}""".formatted(cabeleireiro));
        // Em nome de outra pessoa: só o DONO.
        comoFilha.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "membroId": %d, "descricao": "Corte", "valor": 80, "data": "2026-10-10"}"""
                        .formatted(cabeleireiro, dono.id())))
                .andExpect(status().isForbidden());
        Long doDono = lancar("Despesas pessoais", "Cabeleireiro", "Barba", "40", "2026-10-11");
        comoFilha.perform(delete("/api/lancamentos/{id}", doDono)).andExpect(status().isForbidden());
        // Mas vê tudo.
        comoFilha.perform(get("/api/lancamentos")).andExpect(jsonPath("$", hasSize(2)));
        comoFilha.perform(get("/api/lancamentos").param("membroId", filha.id().toString()))
                .andExpect(jsonPath("$", hasSize(1)));

        // O DONO lança em nome da filha e mexe no lançamento dela.
        postarERetornarId("/api/lancamentos", """
                {"categoriaId": %d, "membroId": %d, "descricao": "Unha", "valor": 50, "data": "2026-10-12"}"""
                .formatted(cabeleireiro, filha.id()));
        mvc.perform(delete("/api/lancamentos/{id}", daFilha)).andExpect(status().isNoContent());
    }

    @Test
    void outraFamiliaNaoVeNemUsaNada() throws Exception {
        Long meu = lancar("Habitação", "Água", "Conta de água", "90", "2026-10-01");
        Membro outro = criarFamilia("outro@teste.com", Plano.PREMIUM);
        var comoOutro = mvcComo(outro);

        comoOutro.perform(get("/api/lancamentos/{id}", meu)).andExpect(status().isNotFound());
        comoOutro.perform(delete("/api/lancamentos/{id}", meu)).andExpect(status().isNotFound());
        comoOutro.perform(get("/api/lancamentos")).andExpect(jsonPath("$", hasSize(0)));
        comoOutro.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "X", "valor": 1, "data": "2026-10-01"}"""
                        .formatted(categoria("Habitação", "Água"))))
                .andExpect(status().isNotFound());
        comoOutro.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "membroId": %d, "descricao": "X", "valor": 1, "data": "2026-10-01"}"""
                        .formatted(categoria(outro, "Habitação", "Água"), dono.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void validacoes() throws Exception {
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "X", "valor": 0, "data": "2026-10-01"}"""
                        .formatted(categoria("Habitação", "Água"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith("valor:")));
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": 99999, "descricao": "X", "valor": 1, "data": "2026-10-01"}"""))
                .andExpect(status().isNotFound());
    }
}
