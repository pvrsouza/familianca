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
    void notaFiscalGuardaCompetenciaEEmissaoEContaNoMesDoPagamento() throws Exception {
        mvc.perform(post("/api/categorias/modelos/pj")).andExpect(status().isCreated());
        Long notas = categoria("Receitas da empresa (PJ)", "Notas fiscais emitidas");

        // Serviço de setembro, nota emitida em 30/09, paga em 06/10: conta em outubro.
        Long id = postarERetornarId("/api/lancamentos", """
                {"categoriaId": %d, "descricao": "TalentFour", "valor": 16464, "data": "2026-10-06",
                 "notaFiscal": {"competencia": "2026-09", "emissao": "2026-09-30"}}""".formatted(notas));
        mvc.perform(get("/api/lancamentos/{id}", id))
                .andExpect(jsonPath("$.competencia").value("2026-10"))
                .andExpect(jsonPath("$.notaFiscal.competencia").value("2026-09"))
                .andExpect(jsonPath("$.notaFiscal.emissao").value("2026-09-30"));

        // Editar sem a nota tira a nota; categoria que não é tributável não aceita nota.
        mvc.perform(put("/api/lancamentos/{id}", id).contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "TalentFour", "valor": 16464, "data": "2026-10-06"}"""
                        .formatted(notas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notaFiscal").doesNotExist());
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Salário", "valor": 9000, "data": "2026-10-05",
                         "notaFiscal": {"competencia": "2026-09"}}""".formatted(categoria("Receitas fixas", "Salário"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "TalentFour", "valor": 100, "data": "2026-10-05",
                         "notaFiscal": {"competencia": "2026-13"}}""".formatted(notas)))
                .andExpect(status().isBadRequest());

        // Recorrência de nota: a data do pagamento é informada a cada mês.
        Long recorrencia = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "TalentFour", "valor": 16464, "dia": 6, "frequencia": "MENSAL",
                 "inicio": "2026-08"}""".formatted(notas));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-08/confirmacao", recorrencia))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-08/confirmacao", recorrencia)
                        .contentType(APPLICATION_JSON).content("""
                                {"data": "2026-09-02", "notaFiscal": {"competencia": "2026-07", "emissao": "2026-07-31"}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").value("2026-09-02"))
                .andExpect(jsonPath("$.notaFiscal.competencia").value("2026-07"))
                .andExpect(jsonPath("$.notaFiscal.emissao").value("2026-07-31"));
    }

    @Test
    void sugereAsMaisComunsEBuscaPeloQueSeDigita() throws Exception {
        lancar("Habitação", "Supermercado", "Feira", "80", "2026-09-01");
        lancar("Habitação", "Supermercado", "Feira", "90", "2026-09-08");
        lancar("Habitação", "Supermercado", "Mercado do mês", "850", "2026-09-05");
        lancar("Habitação", "Supermercado", "mercado do mes ", "700", "2026-10-05");
        lancar("Habitação", "Supermercado", "Mercado do mês", "900", "2026-10-06");
        lancar("Habitação", "Supermercado", "Padaria", "30", "2026-10-07");
        lancar("Habitação", "Supermercado", "Hortifruti 100%", "45", "2026-10-07");
        lancar("Habitação", "Supermercado", "Açougue", "120", "2026-10-08");
        lancar("Habitação", "Energia", "Conta de luz", "200", "2026-10-06");
        Long supermercado = categoria("Habitação", "Supermercado");
        postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Supermercado atacadão", "valor": 400, "dia": 10,
                 "frequencia": "MENSAL", "inicio": "2026-01"}""".formatted(supermercado));
        String url = "/api/lancamentos/descricoes";
        String id = supermercado.toString();

        // Sem termo: só as 3 mais comuns; variações de maiúscula, acento e espaço contam como a mesma.
        mvc.perform(get(url).param("categoriaId", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0]").value("Mercado do mês"))
                .andExpect(jsonPath("$[1]").value("Feira"));

        // Parte do termo, sem acento nem maiúscula; o que começa com o termo vem antes; recorrência entra.
        mvc.perform(get(url).param("categoriaId", id).param("q", "MERC"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("Mercado do mês"))
                .andExpect(jsonPath("$[1]").value("Supermercado atacadão"));
        mvc.perform(get(url).param("categoriaId", id).param("q", "acou"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("Açougue"));
        // Erro de digitação (pg_trgm).
        mvc.perform(get(url).param("categoriaId", id).param("q", "mercdo"))
                .andExpect(jsonPath("$[0]").value("Mercado do mês"));
        mvc.perform(get(url).param("categoriaId", id).param("q", "padara"))
                .andExpect(jsonPath("$[0]").value("Padaria"));
        // % e _ são texto, não curinga.
        mvc.perform(get(url).param("categoriaId", id).param("q", "%"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0]").value("Hortifruti 100%"));
        mvc.perform(get(url).param("categoriaId", id).param("q", "xyzw"))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(url).param("categoriaId", id).param("q", "a".repeat(201)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url).param("categoriaId", categoria("Habitação", "Gás").toString()))
                .andExpect(jsonPath("$", hasSize(0)));

        // Categoria de outra família: 404, sem vazar as descrições.
        Membro outro = criarFamilia("outro@teste.com", Plano.PREMIUM);
        mvc.perform(get(url).param("categoriaId", categoria(outro, "Habitação", "Supermercado").toString())
                        .param("q", "merc"))
                .andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isBadRequest());
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
