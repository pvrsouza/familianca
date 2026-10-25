package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Meta de aporte mês a mês: estimada pelo realizado, definida à mão e encadeada nos meses seguintes. */
@ExtendWith(OutputCaptureExtension.class)
class MetaAporteApiTest extends IntegracaoBase {

    @Test
    void quemMudouAMetaFicaNaAuditoria(CapturedOutput saida) throws Exception {
        mvc.perform(put("/api/familia/metas-aporte/2026-11").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 50}")).andExpect(status().isNoContent());
        mvc.perform(put("/api/familia/metas-aporte/2026-11").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 30}")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/familia/metas-aporte/2026-11")).andExpect(status().isNoContent());
        mvc.perform(put("/api/familia/estimativa-aporte").contentType(APPLICATION_JSON)
                .content("{\"estimativa\": \"MAXIMO\"}")).andExpect(status().isOk());
        String quem = "usuarioId=" + dono.usuarioId();
        assertThat(saida.getOut())
                .contains("evento=META_APORTE_DEFINIDA " + quem)
                .contains("mes=2026-11 de=estimativa para=50%")
                .contains("mes=2026-11 de=50% para=30%")
                .contains("evento=META_APORTE_REMOVIDA " + quem)
                .contains("mes=2026-11 de=30% para=estimativa")
                .contains("evento=REGRA_META_APORTE_ALTERADA " + quem)
                .contains("de=MEDIA_3M para=MAXIMO");
    }

    @Test
    void metaDoMesEstimadaDefinidaEDeVoltaAEstimativa() throws Exception {
        // Hoje é 17/10/2026: janeiro a setembro fecharam. Sobra de 10 mil por mês; 2 mil aplicados só em janeiro.
        mvc.perform(put("/api/familia").contentType(APPLICATION_JSON)
                .content("{\"nome\": \"Família\", \"metaAportePercentual\": 10}")).andExpect(status().isOk());
        mvc.perform(put("/api/saldos/2026-01").contentType(APPLICATION_JSON).content("{\"valor\": 0}"))
                .andExpect(status().isOk());
        postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Salário", "valor": 10000, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-01"}""".formatted(categoria("Receitas fixas", "Salário")));
        Long cdb = postarERetornarId("/api/investimentos", "{\"nome\": \"CDB\", \"tipo\": \"RENDA_FIXA\"}");
        mvc.perform(put("/api/investimentos/{id}/movimentos/2026-01", cdb).contentType(APPLICATION_JSON)
                .content("{\"aporte\": 2000}")).andExpect(status().isOk());
        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.estimativaAporte").value("MEDIA_3M"));

        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(status().isOk())
                // Janeiro: sem base, a meta de partida; realizou 20%.
                .andExpect(jsonPath("$.resumo.metasAporte[0].meta").value(10))
                .andExpect(jsonPath("$.resumo.metasAporte[0].origem").value("PARTIDA"))
                .andExpect(jsonPath("$.resumo.metasAporte[0].realizado").value(20))
                .andExpect(jsonPath("$.resumo.aportePrevisto.valores[0]").value(1000))
                // Fevereiro: a média do realizado de janeiro.
                .andExpect(jsonPath("$.resumo.metasAporte[1].meta").value(20))
                .andExpect(jsonPath("$.resumo.metasAporte[1].origem").value("ESTIMADA"))
                .andExpect(jsonPath("$.resumo.metasAporte[1].base[0].competencia").value("2026-01"))
                .andExpect(jsonPath("$.resumo.metasAporte[1].base[0].realizado").value(true))
                // Março: (20 + 0) / 2.
                .andExpect(jsonPath("$.resumo.metasAporte[2].meta").value(10))
                .andExpect(jsonPath("$.resumo.metasAporte[11].meta").value(0));

        // Novembro em 50%: dezembro passa a ser (set 0 + out 0 + nov 50) / 3.
        mvc.perform(put("/api/familia/metas-aporte/2026-11").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 50}")).andExpect(status().isNoContent());
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.metasAporte[10].meta").value(50))
                .andExpect(jsonPath("$.resumo.metasAporte[10].origem").value("DEFINIDA"))
                .andExpect(jsonPath("$.resumo.metasAporte[11].meta").value(16.67))
                .andExpect(jsonPath("$.resumo.aportePrevisto.valores[11]").value(1667));
        // Janeiro de 2027 continua a média de outubro a dezembro de 2026.
        mvc.perform(get("/api/visao-anual/2027"))
                .andExpect(jsonPath("$.resumo.metasAporte[0].meta").value(22.22))
                .andExpect(jsonPath("$.resumo.metasAporte[0].base[0].competencia").value("2026-10"));

        mvc.perform(delete("/api/familia/metas-aporte/2026-11")).andExpect(status().isNoContent());
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.metasAporte[10].origem").value("ESTIMADA"))
                .andExpect(jsonPath("$.resumo.metasAporte[11].meta").value(0));

        // Não estimar: a meta de partida em todo mês.
        mvc.perform(put("/api/familia/estimativa-aporte").contentType(APPLICATION_JSON)
                .content("{\"estimativa\": \"NENHUMA\"}")).andExpect(jsonPath("$.estimativaAporte").value("NENHUMA"));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.metasAporte[5].meta").value(10))
                .andExpect(jsonPath("$.resumo.metasAporte[5].origem").value("PARTIDA"));
    }

    @Test
    void soODonoDefineEDentroDoAlcance() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        mvcComo(filha).perform(put("/api/familia/metas-aporte/2026-11").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 50}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/familia/metas-aporte/2029-01").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 50}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/familia/metas-aporte/2025-12").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 50}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/familia/metas-aporte/2026-11").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 101}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/familia/metas-aporte/2026-13").contentType(APPLICATION_JSON)
                .content("{\"percentual\": 10}")).andExpect(status().isUnprocessableEntity());
    }
}
