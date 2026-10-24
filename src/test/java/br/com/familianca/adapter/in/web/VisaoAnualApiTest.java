package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O ano de uma família montado pela API, conferido na visão anual. */
class VisaoAnualApiTest extends IntegracaoBase {

    @Test
    void anoDaFamiliaComFluxoInvestimentosEPessoas() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        mvc.perform(put("/api/familia").contentType(APPLICATION_JSON)
                .content("{\"nome\": \"Família\", \"metaAportePercentual\": 10}")).andExpect(status().isOk());
        // Os totais abaixo são os da planilha, sem a estimativa dos próximos meses (testada no fim).
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"NENHUMA\"}"))
                .andExpect(status().isOk());

        // Salário de 10 mil o ano todo (previsto), janeiro confirmado.
        Long salario = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Salário", "valor": 10000, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-01"}""".formatted(categoria("Receitas fixas", "Salário")));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-01/confirmacao", salario))
                .andExpect(status().isCreated());
        lancar("Habitação", "Supermercado", "Mercado", "800", "2026-01-15");
        postarERetornarId(mvcComo(filha), "/api/lancamentos", """
                {"categoriaId": %d, "descricao": "Shopping", "valor": 1200, "data": "2026-02-10"}"""
                .formatted(categoria("Despesas pessoais", "Vestuário")));
        mvc.perform(put("/api/saldos/2026-01").contentType(APPLICATION_JSON).content("{\"valor\": 5000}"))
                .andExpect(status().isOk());

        // Investimento: aporte de 1000 em janeiro, 1% em fevereiro.
        Long cdb = postarERetornarId("/api/investimentos", "{\"nome\": \"CDB\", \"tipo\": \"RENDA_FIXA\"}");
        mvc.perform(put("/api/investimentos/{id}/movimentos/2026-01", cdb).contentType(APPLICATION_JSON)
                .content("{\"aporte\": 1000}")).andExpect(status().isOk());
        mvc.perform(put("/api/investimentos/{id}/movimentos/2026-02", cdb).contentType(APPLICATION_JSON)
                .content("{\"rentabilidade\": 1}")).andExpect(status().isOk());

        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meses[0]").value("2026-01"))
                .andExpect(jsonPath("$.resumo.rendaTotal.ano").value(120000))
                .andExpect(jsonPath("$.resumo.rendaTotal.mediaMensal").value(10000))
                .andExpect(jsonPath("$.resumo.despesasTotal.ano").value(2000))
                .andExpect(jsonPath("$.resumo.aplicacoes.valores[0]").value(1000))
                .andExpect(jsonPath("$.resumo.rendaInvestimentos.valores[1]").value(10))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[0]").value(5000))
                .andExpect(jsonPath("$.resumo.sobraInformada[0]").value(true))
                // jan: 5000 + 10000 − 1000 − 800 = 13200 → sobra de fevereiro.
                .andExpect(jsonPath("$.resumo.fluxoCaixaLiquido.valores[0]").value(13200))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[1]").value(13200))
                .andExpect(jsonPath("$.resumo.fluxoCaixaLiquido.ano").value(122000))
                // 10% × (10000 − 800)
                .andExpect(jsonPath("$.resumo.aportePrevisto.valores[0]").value(920))
                .andExpect(jsonPath("$.receitas.grupos[0].nome").value("Receitas fixas"))
                .andExpect(jsonPath("$.receitas.grupos[0].realizado[0]").value(10000))
                .andExpect(jsonPath("$.receitas.grupos[0].previsto[1]").value(10000))
                .andExpect(jsonPath("$.despesas.grupos[0].nome").value("Habitação"))
                .andExpect(jsonPath("$.despesas.grupos[0].participacao").value(40))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'Supermercado')].rank").value(2))
                // Marcas da categoria para os selos da tabela (recorrente, tributável, regra própria).
                .andExpect(jsonPath("$.receitas.grupos[0].categorias[?(@.nome == 'Salário')].recorrente").value(true))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'Supermercado')].recorrente").value(false))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'Supermercado')].tributavel").value(false))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Despesas pessoais')].categorias[?(@.nome == 'Vestuário')].rank")
                        .value(1))
                .andExpect(jsonPath("$.membros", hasSize(2)))
                .andExpect(jsonPath("$.membros[0].nome").value("Usuário filha"))
                .andExpect(jsonPath("$.membros[0].participacaoDespesas").value(60))
                .andExpect(jsonPath("$.membros[1].receitas.ano").value(120000));

        // Com a média (hoje = 17/out): jan a set já passaram, então supermercado estima 800 ÷ 9 em nov e dez.
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"MEDIA\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'Supermercado')].estimado[9]").value(0.0))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'Supermercado')].estimado[10]").value(88.89))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'Supermercado')].previsto[11]").value(88.89));

        mvc.perform(get("/api/investimentos/carteira").param("ano", "2026"))
                .andExpect(jsonPath("$.saldos[1]").value(1010))
                .andExpect(jsonPath("$.rentabilidadeMensal[1]").value(1))
                .andExpect(jsonPath("$.investimentos[0].investimento.nome").value("CDB"));
    }

    @Test
    void impactoDeUmGastoPequenoNoAno() throws Exception {
        postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Salário", "valor": 7500, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-01"}""".formatted(categoria("Receitas fixas", "Salário")));

        mvc.perform(post("/api/impacto").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 15, \"periodicidade\": \"DIAS_UTEIS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ano").value(2026))
                .andExpect(jsonPath("$.valorAnual").value(3780))
                .andExpect(jsonPath("$.sobreRendaAnual").value(4.2))
                .andExpect(jsonPath("$.diasDeRenda").value(15.3))
                .andExpect(jsonPath("$.sobreDespesasAnuais").doesNotExist())
                .andExpect(jsonPath("$.mensagem").value("R$ 15,00 por dia útil = R$ 3.780,00 no ano "
                        + "(4,2% da renda da família, 15,3 dias de renda). Em 10 anos: R$ 37.800,00."));
    }

    @Test
    void semInvestimentosNoPlanoAVisaoIgnoraAsAplicacoes() throws Exception {
        Membro gratuito = criarFamilia("gratuito@teste.com", Plano.GRATUITO);
        var como = mvcComo(gratuito);
        como.perform(get("/api/investimentos")).andExpect(status().isPaymentRequired());
        como.perform(get("/api/visao-anual"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ano").value(2026))
                .andExpect(jsonPath("$.resumo.aplicacoes.ano").value(0));
        como.perform(get("/api/visao-anual/1999")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void previsaoVaiAteDoisAnosDepoisDoAtual() throws Exception {
        postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Aluguel", "valor": 2000, "dia": 10, "frequencia": "MENSAL",
                 "inicio": "2026-01"}""".formatted(categoria("Habitação", "Aluguel ou financiamento")));

        mvc.perform(get("/api/visao-anual/2028"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.despesasTotal.ano").value(24000));
        mvc.perform(get("/api/visao-anual/2029"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A previsão vai até 2028: anos mais à frente não são mostrados"));
    }
}
