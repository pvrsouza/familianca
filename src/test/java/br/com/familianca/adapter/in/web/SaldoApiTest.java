package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Saldo em conta: sempre o do dia 1º no fluxo, mesmo quando a família informa o de outro dia (hoje = 17/10). */
class SaldoApiTest extends IntegracaoBase {

    @Test
    void saldoDeHojeViraOSaldoDoDiaPrimeiro() throws Exception {
        lancar("Receitas fixas", "Salário", "Salário", "9000", "2026-10-05");
        lancar("Habitação", "Condomínio", "Condomínio", "3000", "2026-10-05");
        lancar("Habitação", "Condomínio", "Condomínio de setembro", "800", "2026-09-30"); // fora do mês

        mvc.perform(get("/api/saldos/movimento").param("ate", "2026-10-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recebido").value(9000))
                .andExpect(jsonPath("$.pago").value(3000));

        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 100000, \"dataReferencia\": \"2026-10-17\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(100000))
                .andExpect(jsonPath("$.dataReferencia").value("2026-10-17"));
        // 100.000 hoje + 3.000 pagos − 9.000 recebidos = 94.000 no dia 1º.
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[9]").value(94000))
                .andExpect(jsonPath("$.resumo.sobraInformada[9]").value(true));

        // Conta paga antes de hoje e lançada depois entra na conta sozinha; a de depois de hoje, não.
        lancar("Habitação", "Condomínio", "Gás", "500", "2026-10-10");
        lancar("Habitação", "Condomínio", "Água", "200", "2026-10-25");
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[9]").value(94500));

        // Sem dia: o valor já é o do dia 1º.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("{\"valor\": 90000}"))
                .andExpect(jsonPath("$.dataReferencia").doesNotExist());
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[9]").value(90000));
    }

    @Test
    void saldoInicialItemAItemEComLugarIncluidoDepois() throws Exception {
        Long corrente = postarERetornarId("/api/locais-saldo", """
                {"nome": "Itaú corrente", "tipo": "CONTA_CORRENTE", "membroId": %d}""".formatted(dono.id()));
        Long mao = postarERetornarId("/api/locais-saldo", """
                {"nome": "Dinheiro na carteira", "tipo": "DINHEIRO"}""");
        mvc.perform(post("/api/locais-saldo").contentType(APPLICATION_JSON).content("""
                        {"nome": "itaú CORRENTE", "tipo": "POUPANCA"}"""))
                .andExpect(status().isConflict());

        // Com itens, o total é a soma deles (o valor enviado é ignorado).
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON).content("""
                        {"valor": 1, "porLocal": [{"localId": %d, "valor": 50000}, {"localId": %d, "valor": 300}]}"""
                        .formatted(corrente, mao)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(50300))
                .andExpect(jsonPath("$.porLocal.length()").value(2));

        // Alguém entra na família depois e traz o cofrinho: entra no mesmo saldo inicial, retroativo.
        Long cofrinho = postarERetornarId("/api/locais-saldo", """
                {"nome": "Cofrinho do João", "tipo": "COFRINHO"}""");
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON).content("""
                        {"porLocal": [{"localId": %d, "valor": 50000}, {"localId": %d, "valor": 300},
                                      {"localId": %d, "valor": 120}]}""".formatted(corrente, mao, cofrinho)))
                .andExpect(jsonPath("$.valor").value(50420))
                .andExpect(jsonPath("$.porLocal.length()").value(3));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[8]").value(50420))
                // Outubro sem saldo informado: segue a sobra de setembro.
                .andExpect(jsonPath("$.resumo.sobraInformada[9]").value(false));

        // Lugar que já entrou num saldo não sai: desativa.
        mvc.perform(delete("/api/locais-saldo/{id}", mao)).andExpect(status().isConflict());
        mvc.perform(put("/api/locais-saldo/{id}", mao).contentType(APPLICATION_JSON).content("""
                        {"nome": "Dinheiro na carteira", "tipo": "DINHEIRO", "ativo": false}"""))
                .andExpect(jsonPath("$.ativo").value(false));

        // Sem itens e sem valor: nada a guardar.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void lugarDeOutraFamiliaNaoEntra() throws Exception {
        Membro outro = criarFamilia("outra@teste.com", Plano.PREMIUM);
        Long deles = postarERetornarId(mvcComo(outro), "/api/locais-saldo", """
                {"nome": "Conta deles", "tipo": "OUTRO"}""");
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content("{\"porLocal\": [{\"localId\": %d, \"valor\": 10}]}".formatted(deles)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/locais-saldo/{id}", deles)).andExpect(status().isNotFound());
        mvc.perform(post("/api/locais-saldo").contentType(APPLICATION_JSON).content("""
                        {"nome": "Minha", "tipo": "OUTRO", "membroId": %d}""".formatted(outro.id())))
                .andExpect(status().isNotFound());
    }

    @Test
    void diaDoSaldoPrecisaSerDoMesENaoFuturo() throws Exception {
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 1000, \"dataReferencia\": \"2026-10-18\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 1000, \"dataReferencia\": \"2026-09-15\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void soODonoInformaOuConsultaOMovimento() throws Exception {
        Membro membro = adicionarMembro(dono, "membro@teste.com", PapelFamilia.MEMBRO);
        mvcComo(membro).perform(get("/api/saldos/movimento").param("ate", "2026-10-17"))
                .andExpect(status().isForbidden());
        mvcComo(membro).perform(get("/api/locais-saldo")).andExpect(status().isOk());
        mvcComo(membro).perform(post("/api/locais-saldo").contentType(APPLICATION_JSON).content("""
                        {"nome": "Minha conta", "tipo": "CONTA_CORRENTE"}"""))
                .andExpect(status().isForbidden());
    }
}
