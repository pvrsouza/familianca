package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvestimentoApiTest extends IntegracaoBase {

    private static final String SALDOS = """
            {"saldos": [%s]}""";

    @Test
    void atualizaOsSaldosDoMesDeUmaVez() throws Exception {
        Long cdb = postarERetornarId("/api/investimentos", "{\"nome\": \"CDB\", \"tipo\": \"RENDA_FIXA\"}");
        Long tesouro = postarERetornarId("/api/investimentos", "{\"nome\": \"Tesouro\", \"tipo\": \"RENDA_FIXA\"}");

        // Hoje é 17/10: o mês de referência é setembro.
        mvc.perform(get("/api/investimentos/saldos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value("2026-09"))
                .andExpect(jsonPath("$.referencia").value("2026-09"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.atualizados").value(0));

        mvc.perform(put("/api/investimentos/saldos/2026-09").contentType(APPLICATION_JSON).content(SALDOS.formatted("""
                        {"investimentoId": %d, "saldo": 50000}, {"investimentoId": %d, "saldo": 20000}"""
                        .formatted(cdb, tesouro))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atualizados").value(2));

        // Outubro: o CDB rendeu 450 com 1.000 colocados; o Tesouro ficou igual e também conta como atualizado.
        mvc.perform(put("/api/investimentos/saldos/2026-10").contentType(APPLICATION_JSON).content(SALDOS.formatted("""
                        {"investimentoId": %d, "saldo": 51450, "aporte": 1000}, {"investimentoId": %d, "saldo": 20000}"""
                        .formatted(cdb, tesouro))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value("2026-10"))
                .andExpect(jsonPath("$.atualizados").value(2))
                .andExpect(jsonPath("$.linhas[0].investimento.nome").value("CDB"))
                .andExpect(jsonPath("$.linhas[0].saldoAnterior").value(50000))
                .andExpect(jsonPath("$.linhas[0].aporte").value(1000));
        mvc.perform(get("/api/investimentos/carteira").param("ano", "2026"))
                .andExpect(jsonPath("$.investimentos[0].abertura.valor").value(50000))
                .andExpect(jsonPath("$.investimentos[0].rendimentos[9]").value(450))
                .andExpect(jsonPath("$.investimentos[1].rendimentos[9]").value(0));

        // Investimento criado depois: não aparece em meses antes do primeiro saldo dele.
        Long acoes = postarERetornarId("/api/investimentos", "{\"nome\": \"Ações\", \"tipo\": \"ACOES\"}");
        mvc.perform(put("/api/investimentos/saldos/2026-10").contentType(APPLICATION_JSON)
                .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 5000}".formatted(acoes))));
        mvc.perform(get("/api/investimentos/saldos").param("mes", "2026-09"))
                .andExpect(jsonPath("$.linhas", hasSize(2)));

        // Mês que ainda não chegou, investimento repetido e saldo negativo.
        mvc.perform(put("/api/investimentos/saldos/2026-11").contentType(APPLICATION_JSON)
                        .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 1}".formatted(cdb))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/investimentos/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 1}, {\"investimentoId\": %d, \"saldo\": 2}"
                                .formatted(cdb, cdb))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/investimentos/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": -1}".formatted(cdb))))
                .andExpect(status().isBadRequest());

        // Quem participa consulta, mas só quem organiza atualiza.
        Membro participa = adicionarMembro(dono, "participa@teste.com", PapelFamilia.MEMBRO);
        mvcComo(participa).perform(get("/api/investimentos/saldos")).andExpect(status().isOk());
        mvcComo(participa).perform(put("/api/investimentos/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 1}".formatted(cdb))))
                .andExpect(status().isForbidden());
    }

    @Test
    void encerrarResgataTudoEZeraDaliEmDiante() throws Exception {
        Long cdb = postarERetornarId("/api/investimentos", "{\"nome\": \"CDB\", \"tipo\": \"RENDA_FIXA\"}");
        mvc.perform(put("/api/investimentos/saldos/2026-08").contentType(APPLICATION_JSON)
                .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 10000}".formatted(cdb))));
        mvc.perform(put("/api/investimentos/saldos/2026-09").contentType(APPLICATION_JSON)
                .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 10100}".formatted(cdb))));

        mvc.perform(post("/api/investimentos/{id}/encerramento", cdb).contentType(APPLICATION_JSON)
                        .content("{\"mes\": \"2026-08\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/investimentos/{id}/encerramento", cdb).contentType(APPLICATION_JSON)
                        .content("{\"mes\": \"2026-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
        // Outubro: resgate de 10.100 (aplicação negativa no fluxo), sem rendimento; novembro e dezembro zerados.
        mvc.perform(get("/api/investimentos/carteira").param("ano", "2026"))
                .andExpect(jsonPath("$.aportes[9]").value(-10100))
                .andExpect(jsonPath("$.rendimentos[9]").value(0))
                .andExpect(jsonPath("$.saldos[9]").value(0))
                .andExpect(jsonPath("$.saldos[11]").value(0));
        mvc.perform(post("/api/investimentos/{id}/encerramento", cdb).contentType(APPLICATION_JSON)
                        .content("{\"mes\": \"2026-10\"}"))
                .andExpect(status().isConflict());

        // Reativado, o saldo novo num investimento zerado entra como abertura, não como rendimento.
        mvc.perform(put("/api/investimentos/{id}", cdb).contentType(APPLICATION_JSON)
                .content("{\"nome\": \"CDB\", \"tipo\": \"RENDA_FIXA\", \"ativo\": true}"));
        relogio.definir(java.time.LocalDate.of(2026, 11, 10));
        mvc.perform(put("/api/investimentos/saldos/2026-11").contentType(APPLICATION_JSON)
                .content(SALDOS.formatted("{\"investimentoId\": %d, \"saldo\": 5000}".formatted(cdb))));
        mvc.perform(get("/api/investimentos/carteira").param("ano", "2026"))
                .andExpect(jsonPath("$.aportes[9]").value(-10100))
                .andExpect(jsonPath("$.rendimentos[10]").value(0))
                .andExpect(jsonPath("$.saldos[10]").value(5000));
    }
}
