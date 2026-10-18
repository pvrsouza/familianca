package br.com.familianca.adapter.in.web;

import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
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
    void saldoDeHojeParaConferirNaoMudaOSaldoDoDiaPrimeiro() throws Exception {
        lancar("Habitação", "Condomínio", "Condomínio", "3", "2026-10-05");

        // 10 no dia 1º e 5 hoje: são dois saldos, guardados lado a lado. O fluxo começa com os 10.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 10, \"conferencia\": {\"dia\": \"2026-10-17\", \"valor\": 5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(10))
                .andExpect(jsonPath("$.dataReferencia").doesNotExist())
                .andExpect(jsonPath("$.conferencia.dia").value("2026-10-17"))
                .andExpect(jsonPath("$.conferencia.valor").value(5));
        mvc.perform(get("/api/saldos").param("ano", "2026"))
                .andExpect(jsonPath("$[0].valor").value(10))
                .andExpect(jsonPath("$[0].conferencia.valor").value(5));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[9]").value(10));

        // Guardar de novo sem conferência tira o saldo de hoje.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("{\"valor\": 10}"))
                .andExpect(jsonPath("$.conferencia").doesNotExist());

        // Conferência só junto do saldo do dia 1º, dentro do mês e não no futuro.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("""
                        {"valor": 5, "dataReferencia": "2026-10-17", "conferencia": {"dia": "2026-10-17", "valor": 5}}"""))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("""
                        {"valor": 10, "conferencia": {"dia": "2026-09-30", "valor": 5}}"""))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("""
                        {"valor": 10, "conferencia": {"dia": "2026-10-20", "valor": 5}}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void saldoInicialDeQualquerMesQueJaChegouEASobraPassaDeUmAnoParaOOutro() throws Exception {
        semSaldoInicial(dono);
        // Mês que ainda não chegou não serve de saldo inicial (hoje = 17/10/2026).
        mvc.perform(put("/api/saldos/2026-11").contentType(APPLICATION_JSON).content("{\"valor\": 1000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("mês atual ou de um mês que já passou")));

        // Começar num mês de um ano que já passou vale; janeiro do ano seguinte continua de onde dezembro parou.
        mvc.perform(put("/api/saldos/2025-11").contentType(APPLICATION_JSON).content("{\"valor\": 1000}"))
                .andExpect(status().isOk());
        lancar("Habitação", "Condomínio", "Condomínio", "200", "2025-12-10");
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.resumo.saldoAntesDoAno").value(true))
                .andExpect(jsonPath("$.resumo.sobraInformada[0]").value(false))
                .andExpect(jsonPath("$.resumo.sobraMesAnterior.valores[0]").value(800));
        mvc.perform(get("/api/visao-anual/2025"))
                .andExpect(jsonPath("$.resumo.saldoAntesDoAno").value(false));

        // Depois do saldo inicial, corrigir um mês adiante é permitido (é correção, não começo).
        mvc.perform(put("/api/saldos/2026-11").contentType(APPLICATION_JSON).content("{\"valor\": 500}"))
                .andExpect(status().isOk());
    }

    @Test
    void mesQueJaPassouSoAceitaOSaldoDoDiaPrimeiro() throws Exception {
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 100, \"dataReferencia\": \"2026-09-15\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail", containsString("só o saldo do dia 1º")));
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 100, \"conferencia\": {\"dia\": \"2026-09-15\", \"valor\": 90}}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON).content("{\"valor\": 100}"))
                .andExpect(status().isOk());

        // Guardado com dia quando setembro era o mês corrente: continua valendo e se corrige mantendo o dia.
        jdbc.update("update saldo_mes set data_referencia = ? where familia_id = ? and competencia = '2026-09'",
                java.sql.Date.valueOf("2026-09-15"), dono.familiaId());
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 120, \"dataReferencia\": \"2026-09-15\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/saldos/2026-09").contentType(APPLICATION_JSON)
                        .content("{\"valor\": 120, \"dataReferencia\": \"2026-09-20\"}"))
                .andExpect(status().isUnprocessableEntity());
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
    void nadaSeLancaAntesDoSaldoInicial() throws Exception {
        semSaldoInicial(dono);
        Membro membro = adicionarMembro(dono, "membro@teste.com", PapelFamilia.MEMBRO);
        Long mercado = categoria("Habitação", "Supermercado");
        String lancamento = """
                {"categoriaId": %d, "descricao": "Mercado", "valor": 100, "data": "2026-10-05"}""".formatted(mercado);

        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.temSaldoInicial").value(false));
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content(lancamento))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("informem quanto a família tem hoje")));
        mvc.perform(post("/api/lancamentos/parcelados").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Geladeira", "valorTotal": 3000, "parcelas": 3,
                         "primeiraData": "2026-10-05"}""".formatted(mercado)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/recorrencias").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Mercado", "valor": 900, "dia": 5, "frequencia": "MENSAL",
                         "inicio": "2026-10"}""".formatted(mercado)))
                .andExpect(status().isUnprocessableEntity());
        // Quem participa não informa o saldo: a mensagem diz quem precisa fazer.
        mvcComo(membro).perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content(lancamento))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("quem organiza")));

        // Zero vale como saldo inicial.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("{\"valor\": 0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.temSaldoInicial").value(true));
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content(lancamento))
                .andExpect(status().isCreated());
        mvcComo(membro).perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content(lancamento))
                .andExpect(status().isCreated());
    }

    @Test
    void oSaldoInicialNaoSaiSoSeCorrige() throws Exception {
        mvc.perform(delete("/api/saldos/" + COMPETENCIA_SALDO_INICIAL)).andExpect(status().isUnprocessableEntity());
        // Com outro mês informado, um deles pode sair.
        mvc.perform(put("/api/saldos/2026-10").contentType(APPLICATION_JSON).content("{\"valor\": -250}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/saldos/2026-10")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/saldos/" + COMPETENCIA_SALDO_INICIAL)).andExpect(status().isUnprocessableEntity());
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
