package br.com.familianca.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecorrenciaApiTest extends IntegracaoBase {

    private Long streaming() throws Exception {
        return postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Streaming", "valor": 55.90, "dia": 10, "frequencia": "MENSAL",
                 "inicio": "2026-08"}""".formatted(categoria("Outras despesas fixas", "Assinaturas e streaming")));
    }

    @Test
    void criaEMostraOPesoNoAno() throws Exception {
        Long id = streaming();
        mvc.perform(get("/api/recorrencias/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("DESPESA"))
                .andExpect(jsonPath("$.fim").doesNotExist())
                // ago a dez de 2026: 5 × 55,90
                .andExpect(jsonPath("$.valorNoAno").value(279.50))
                .andExpect(jsonPath("$.valorAnualizado").value(670.80));
        mvc.perform(get("/api/recorrencias")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/recorrencias").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "X", "valor": 10, "dia": 10, "frequencia": "MENSAL",
                         "inicio": "2026-08", "fim": "2026-07"}""".formatted(categoria("Habitação", "Água"))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/recorrencias").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "X", "valor": 10, "dia": 10, "frequencia": "MENSAL",
                         "inicio": "agosto"}""".formatted(categoria("Habitação", "Água"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Competência inválida: agosto. Use AAAA-MM"));
    }

    @Test
    void confirmacaoSoMudaValorDataEObservacao() throws Exception {
        Long id = streaming();
        String corpo = mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", id))
                .andReturn().getResponse().getContentAsString();
        Long lancamento = ((Number) JsonPath.read(corpo, "$.id")).longValue();
        Long assinaturas = categoria("Outras despesas fixas", "Assinaturas e streaming");
        String edicao = """
                {"categoriaId": %d, "descricao": "%s", "valor": 59.90, "data": "2026-09-12"}""";

        mvc.perform(put("/api/lancamentos/{id}", lancamento).contentType(APPLICATION_JSON)
                        .content(edicao.formatted(categoria("Habitação", "Água"), "Streaming")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("É a confirmação de Streaming")));
        mvc.perform(put("/api/lancamentos/{id}", lancamento).contentType(APPLICATION_JSON)
                        .content(edicao.formatted(assinaturas, "Netflix")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/lancamentos/{id}", lancamento).contentType(APPLICATION_JSON)
                        .content(edicao.formatted(assinaturas, "Streaming")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(59.90))
                .andExpect(jsonPath("$.data").value("2026-09-12"));
    }

    @Test
    void contasAPagarDoMes() throws Exception {
        String conta = """
                {"categoriaId": %d, "descricao": "%s", "valor": %s, "dia": %d, "frequencia": "MENSAL",
                 "inicio": "%s", "semVencimento": %s, "formaPagamento": %s}""";
        Long condominio = postarERetornarId("/api/recorrencias", conta.formatted(
                categoria("Habitação", "Condomínio"), "Condomínio", "800", 5, "2026-09", false, "\"BOLETO_PIX\""));
        Long agua = postarERetornarId("/api/recorrencias", conta.formatted(
                categoria("Habitação", "Água"), "Água", "90", 20, "2026-10", false, null));
        postarERetornarId("/api/recorrencias", conta.formatted(
                categoria("Habitação", "Supermercado"), "Feira", "600", 1, "2026-10", true, null));
        postarERetornarId("/api/recorrencias", conta.formatted(
                categoria("Habitação", "Internet"), "Internet", "120", 15, "2026-10", false, "\"DEBITO_AUTOMATICO\""));
        postarERetornarId("/api/recorrencias", conta.formatted(
                categoria("Outras despesas fixas", "Assinaturas e streaming"), "Streaming", "55.90", 10, "2026-10",
                false, "\"CARTAO_CREDITO\""));
        postarERetornarId("/api/recorrencias", conta.formatted(
                categoria("Receitas fixas", "Salário"), "Salário", "5000", 5, "2026-10", false, null));

        // Hoje é 17/10. A feira (sem forma, sem vencimento) e o salário (receita) ficam de fora.
        mvc.perform(get("/api/recorrencias/contas-a-pagar").param("mes", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aPagar", hasSize(2)))
                .andExpect(jsonPath("$.aPagar[0].recorrencia.descricao").value("Condomínio"))
                .andExpect(jsonPath("$.aPagar[0].vencimento").value("2026-10-05"))
                .andExpect(jsonPath("$.aPagar[0].valorPrevisto").value(800))
                .andExpect(jsonPath("$.aPagar[0].pagamento").doesNotExist())
                .andExpect(jsonPath("$.aPagar[1].recorrencia.descricao").value("Água"))
                .andExpect(jsonPath("$.paraConferir", hasSize(2)))
                .andExpect(jsonPath("$.paraConferir[0].recorrencia.descricao").value("Streaming"))
                .andExpect(jsonPath("$.paraConferir[0].vencimento").doesNotExist())
                .andExpect(jsonPath("$.paraConferir[1].recorrencia.descricao").value("Internet"))
                // Setembro do condomínio venceu e não foi pago.
                .andExpect(jsonPath("$.anterioresEmAberto", hasSize(1)))
                .andExpect(jsonPath("$.anterioresEmAberto[0].competencia").value("2026-09"))
                .andExpect(jsonPath("$.anterioresEmAberto[0].contas").value(1));

        // Pagar = confirmar; pago com atraso e pulado ("não houve") saem da conta do que falta.
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/confirmacao", condominio)
                .contentType(APPLICATION_JSON).content("{\"data\": \"2026-10-08\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/pulo", agua)).andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/contas-a-pagar").param("mes", "2026-10"))
                .andExpect(jsonPath("$.aPagar", hasSize(1)))
                .andExpect(jsonPath("$.aPagar[0].pagamento.valor").value(800))
                .andExpect(jsonPath("$.aPagar[0].pagamento.diasAtraso").value(3));

        // Outro mês: sem o aviso dos anteriores.
        mvc.perform(get("/api/recorrencias/contas-a-pagar").param("mes", "2026-09"))
                .andExpect(jsonPath("$.aPagar", hasSize(1)))
                .andExpect(jsonPath("$.paraConferir", hasSize(0)))
                .andExpect(jsonPath("$.anterioresEmAberto", hasSize(0)));
        mvc.perform(get("/api/recorrencias/contas-a-pagar").param("mes", "2029-01"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/recorrencias/contas-a-pagar").param("mes", "outubro"))
                .andExpect(status().isUnprocessableEntity());

        // Quem participa vê tudo, mas só paga as contas em que é o responsável; outra família não vê nada.
        Membro participa = adicionarMembro(dono, "participa@teste.com", PapelFamilia.MEMBRO);
        mvcComo(participa).perform(get("/api/recorrencias/contas-a-pagar").param("mes", "2026-10"))
                .andExpect(jsonPath("$.paraConferir", hasSize(2)));
        mvcComo(participa).perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", condominio))
                .andExpect(status().isForbidden());
        Membro outra = criarFamilia("outra@teste.com", Plano.PREMIUM);
        mvcComo(outra).perform(get("/api/recorrencias/contas-a-pagar").param("mes", "2026-10"))
                .andExpect(jsonPath("$.aPagar", hasSize(0)))
                .andExpect(jsonPath("$.paraConferir", hasSize(0)))
                .andExpect(jsonPath("$.anterioresEmAberto", hasSize(0)));
    }

    @Test
    void ocorrenciaDepoisDoUltimoAnoPrevistoNaoSeMexe() throws Exception {
        Long id = streaming();
        // Hoje é 2026: a previsão vai até 2028.
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2028-12/pulo", id)).andExpect(status().isOk());
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2029-01/pulo", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A previsão vai até 2028: 2029-01 ainda não aparece"));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2029-01/confirmacao", id))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2029-01/valor", id).contentType(APPLICATION_JSON)
                .content("{\"valor\": 60}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2029-01/vencimento", id).contentType(APPLICATION_JSON)
                .content("{\"vencimento\": \"2029-01-12\"}")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void confirmaPulaEAcompanhaPendentes() throws Exception {
        Long id = streaming();
        // Hoje é 17/10: agosto, setembro e outubro (dia 10) já passaram.
        mvc.perform(get("/api/recorrencias/pendentes"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].competencia").value("2026-08"))
                .andExpect(jsonPath("$[0].data").value("2026-08-10"))
                .andExpect(jsonPath("$[0].valorPrevisto").value(55.90));

        String corpo = mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-08/confirmacao", id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(55.90))
                .andExpect(jsonPath("$.data").value("2026-08-10"))
                .andExpect(jsonPath("$.recorrenciaId").value(id))
                .andExpect(jsonPath("$.competenciaRecorrencia").value("2026-08"))
                .andReturn().getResponse().getContentAsString();
        Long confirmadoAgosto = ((Number) JsonPath.read(corpo, "$.id")).longValue();
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", id).contentType(APPLICATION_JSON)
                        .content("{\"valor\": 59.90, \"data\": \"2026-09-12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(59.90));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", id))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/pulo", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puladas[0]").value("2026-10"))
                // ago e set pelo cadastrado (55,90); nov e dez pela média das confirmações (55,90 e 59,90 = 57,90).
                .andExpect(jsonPath("$.valorNoAno").value(227.60));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/confirmacao", id))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/recorrencias/pendentes")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/recorrencias/{id}/ocorrencias/2026-10/pulo", id)).andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/pendentes")).andExpect(jsonPath("$", hasSize(1)));

        // Com ocorrências confirmadas não exclui: encerra.
        mvc.perform(delete("/api/recorrencias/{id}", id)).andExpect(status().isConflict());
        mvc.perform(put("/api/recorrencias/{id}", id).contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Streaming", "valor": 55.90, "dia": 10, "frequencia": "MENSAL",
                         "inicio": "2026-08", "fim": "2026-10"}""".formatted(
                        categoria("Outras despesas fixas", "Assinaturas e streaming"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fim").value("2026-10"));

        // Excluir a confirmação devolve a ocorrência às pendentes.
        mvc.perform(delete("/api/lancamentos/{id}", confirmadoAgosto)).andExpect(status().isNoContent());
        mvc.perform(get("/api/recorrencias/pendentes")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void confirmaSozinhaODebitoAutomatico() throws Exception {
        Long condominio = categoria("Habitação", "Condomínio");
        String corpo = """
                {"categoriaId": %d, "descricao": "Condomínio", "valor": 200, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-08", "confirmarSozinha": %s}""";
        // Ligada hoje (17/10): agosto, setembro e outubro já tinham vencido e continuam para conferir.
        Long id = postarERetornarId("/api/recorrencias", corpo.formatted(condominio, "true"));
        mvc.perform(get("/api/recorrencias/{id}", id))
                .andExpect(jsonPath("$.confirmarSozinha").value(true))
                .andExpect(jsonPath("$.confirmarSozinhaDesde").value("2026-10-17"));
        mvc.perform(get("/api/recorrencias/pendentes")).andExpect(jsonPath("$", hasSize(3)));

        // Em 06/12, novembro (05/11) e dezembro (05/12) viraram lançamento sozinhos, com o valor previsto.
        relogio.definir(java.time.LocalDate.of(2026, 12, 6));
        mvc.perform(get("/api/recorrencias/pendentes")).andExpect(jsonPath("$", hasSize(3)));
        String lista = mvc.perform(get("/api/lancamentos?de=2026-11-01&ate=2026-12-31"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].data").value("2026-12-05"))
                .andExpect(jsonPath("$[0].valor").value(200.00))
                .andExpect(jsonPath("$[0].recorrenciaId").value(id))
                .andExpect(jsonPath("$[1].competenciaRecorrencia").value("2026-11"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Habitação')].previsto[11]").value(0.0));

        // Excluir a confirmação automática = não houve no mês; não volta a ser confirmada sozinha.
        Long dezembro = ((Number) JsonPath.read(lista, "$[0].id")).longValue();
        mvc.perform(delete("/api/lancamentos/{id}", dezembro)).andExpect(status().isNoContent());
        mvc.perform(get("/api/lancamentos?de=2026-11-01&ate=2026-12-31")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/recorrencias/{id}", id)).andExpect(jsonPath("$.puladas[0]").value("2026-12"));

        // Editar mantém a data em que foi ligada; mandar sem a opção desliga.
        mvc.perform(put("/api/recorrencias/{id}", id).contentType(APPLICATION_JSON)
                        .content(corpo.formatted(condominio, "true")))
                .andExpect(jsonPath("$.confirmarSozinhaDesde").value("2026-10-17"));
        mvc.perform(put("/api/recorrencias/{id}", id).contentType(APPLICATION_JSON)
                        .content(corpo.formatted(condominio, "false")))
                .andExpect(jsonPath("$.confirmarSozinha").value(false))
                .andExpect(jsonPath("$.confirmarSozinhaDesde").doesNotExist());

        // Receita que se repete precisa ser conferida.
        mvc.perform(post("/api/recorrencias").contentType(APPLICATION_JSON)
                        .content(corpo.formatted(categoria("Receitas fixas", "Salário"), "true")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Só despesa confirma sozinha: receita que se repete precisa ser conferida"));
    }

    @Test
    void vencimentoFormaDePagamentoEAtraso() throws Exception {
        // Hoje = 17/10/2026. Condomínio por boleto, vence dia 10.
        Long condominio = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Condomínio", "valor": 600, "dia": 10, "frequencia": "MENSAL",
                 "inicio": "2026-09", "formaPagamento": "BOLETO_PIX"}""".formatted(categoria("Habitação", "Condomínio")));
        mvc.perform(get("/api/recorrencias/{id}", condominio))
                .andExpect(jsonPath("$.formaPagamento").value("BOLETO_PIX"))
                .andExpect(jsonPath("$.temVencimento").value(true))
                .andExpect(jsonPath("$.semVencimento").value(false));

        // Outubro veio para o dia 20: ainda não venceu, então sai das pendentes.
        mvc.perform(get("/api/recorrencias/pendentes"))
                .andExpect(jsonPath("$[?(@.competencia == '2026-10')].vencimento").value("2026-10-10"));
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-10/vencimento", condominio)
                        .contentType(APPLICATION_JSON).content("{\"vencimento\": \"2026-10-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vencimentosAjustados[0].competencia").value("2026-10"))
                .andExpect(jsonPath("$.vencimentosAjustados[0].vencimento").value("2026-10-20"));
        mvc.perform(get("/api/recorrencias/pendentes"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].competencia").value("2026-09"));
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-10/vencimento", condominio)
                        .contentType(APPLICATION_JSON).content("{\"vencimento\": \"2027-03-01\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Setembro pago dia 15 (vencia dia 10): 5 dias de atraso. Outubro pago antes do vencimento ajustado: em dia.
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", condominio)
                        .contentType(APPLICATION_JSON).content("{\"data\": \"2026-09-15\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vencimento").value("2026-09-10"))
                .andExpect(jsonPath("$.diasAtraso").value(5));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/confirmacao", condominio)
                        .contentType(APPLICATION_JSON).content("{\"data\": \"2026-10-17\"}"))
                .andExpect(jsonPath("$.vencimento").value("2026-10-20"))
                .andExpect(jsonPath("$.diasAtraso").value(0));

        // Cobrada no cartão: sem vencimento próprio, nem para ajustar.
        Long streaming = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Streaming", "valor": 55.90, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-09", "formaPagamento": "CARTAO_CREDITO"}"""
                .formatted(categoria("Outras despesas fixas", "Assinaturas e streaming")));
        mvc.perform(get("/api/recorrencias/{id}", streaming)).andExpect(jsonPath("$.temVencimento").value(false));
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-10/vencimento", streaming)
                        .contentType(APPLICATION_JSON).content("{\"vencimento\": \"2026-10-20\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", streaming))
                .andExpect(jsonPath("$.vencimento").doesNotExist())
                .andExpect(jsonPath("$.diasAtraso").doesNotExist());

        // Receita não tem forma de pagamento nem vencimento.
        mvc.perform(post("/api/recorrencias").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Salário", "valor": 9000, "dia": 5, "frequencia": "MENSAL",
                         "inicio": "2026-01", "formaPagamento": "BOLETO_PIX"}""".formatted(categoria("Receitas fixas", "Salário"))))
                .andExpect(status().isUnprocessableEntity());
        Long salario = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Salário", "valor": 9000, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2026-01"}""".formatted(categoria("Receitas fixas", "Salário")));
        mvc.perform(get("/api/recorrencias/{id}", salario)).andExpect(jsonPath("$.temVencimento").value(false));
    }

    @Test
    void valorPrevistoDiferenteSoNumMesSemConfirmar() throws Exception {
        Long id = streaming();
        // Dezembro veio por 70: o ano passa de 5 × 55,90 = 279,50 para 279,50 − 55,90 + 70 = 293,60.
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-12/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": 70}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorNoAno").value(293.60))
                .andExpect(jsonPath("$.valoresAjustados[0].competencia").value("2026-12"))
                .andExpect(jsonPath("$.valoresAjustados[0].valor").value(70));

        // Nulo volta ao previsto de sempre.
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-12/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": null}"))
                .andExpect(jsonPath("$.valorNoAno").value(279.50))
                .andExpect(jsonPath("$.valoresAjustados", hasSize(0)));

        // Confirmar sem valor usa o ajustado; depois de confirmada, muda-se o lançamento (409).
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-09/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": 61.50}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", id))
                .andExpect(jsonPath("$.valor").value(61.50));
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-09/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": 80}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-12/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": -1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-07/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": 10}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void valorPrevistoDaquiEmDianteGuardaOsMesesAntes() throws Exception {
        Long id = streaming();
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-08/confirmacao", id)).andExpect(status().isCreated());
        // De novembro em diante, 60. Setembro e outubro (previstos) ficam com 55,90; agosto já virou lançamento.
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-11/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": 60, \"daquiEmDiante\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value(60))
                .andExpect(jsonPath("$.valoresAjustados", hasSize(2)))
                .andExpect(jsonPath("$.valoresAjustados[0].competencia").value("2026-09"))
                .andExpect(jsonPath("$.valoresAjustados[0].valor").value(55.90))
                .andExpect(jsonPath("$.valoresAjustados[1].competencia").value("2026-10"));
        mvc.perform(get("/api/recorrencias/pendentes"))
                .andExpect(jsonPath("$[0].competencia").value("2026-09"))
                .andExpect(jsonPath("$[0].valorPrevisto").value(55.90));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-12/confirmacao", id))
                .andExpect(jsonPath("$.valor").value(60));
        mvc.perform(put("/api/recorrencias/{id}/ocorrencias/2026-11/valor", id)
                        .contentType(APPLICATION_JSON).content("{\"valor\": null, \"daquiEmDiante\": true}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void contaAvulsaUmaVezSo() throws Exception {
        Long iptu = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "IPTU cota única", "valor": 1800, "dia": 25, "frequencia": "UNICA",
                 "inicio": "2026-11", "fim": "2027-12"}""".formatted(categoria("Habitação", "IPTU e taxas")));
        mvc.perform(get("/api/recorrencias/{id}", iptu))
                .andExpect(jsonPath("$.frequencia").value("UNICA"))
                .andExpect(jsonPath("$.fim").value("2026-11"))
                .andExpect(jsonPath("$.valorNoAno").value(1800))
                .andExpect(jsonPath("$.valorAnualizado").value(1800));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'IPTU e taxas')].previsto[10]").value(1800.0))
                .andExpect(jsonPath("$.despesas.grupos[0].categorias[?(@.nome == 'IPTU e taxas')].previsto[11]").value(0.0));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-12/confirmacao", iptu))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void semConfirmacaoPodeExcluir() throws Exception {
        Long id = streaming();
        mvc.perform(delete("/api/recorrencias/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/recorrencias/{id}", id)).andExpect(status().isNotFound());
    }

    /** Hoje = 17/out/2026. DAS cadastrado em 3.000 desde julho, confirmado com valores diferentes de jul a set. */
    @Test
    void contaDeValorVariavelPrevistaPelaRegra() throws Exception {
        Long das = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "DAS", "valor": 3000, "dia": 20, "frequencia": "MENSAL",
                 "inicio": "2026-07"}""".formatted(categoria("Outras despesas fixas", "Tarifas bancárias")));
        // Despesa nova já nasce com a média dos últimos 3 meses como regra própria; aqui ela passa a seguir a família.
        mvc.perform(get("/api/recorrencias/{id}", das)).andExpect(jsonPath("$.estimativa").value("MEDIA_3M"));
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                .content("{\"estimativa\": null}")).andExpect(status().isOk());
        for (String[] c : new String[][]{{"2026-07", "3184.15"}, {"2026-08", "4959.84"}, {"2026-09", "1511.61"}}) {
            mvc.perform(post("/api/recorrencias/{id}/ocorrencias/{c}/confirmacao", das, c[0])
                    .contentType(APPLICATION_JSON).content("{\"valor\": %s}".formatted(c[1])))
                    .andExpect(status().isCreated());
        }
        // Média das três últimas confirmações (padrão da família): 3.218,53, do mês atual em diante.
        mvc.perform(get("/api/recorrencias/{id}", das))
                .andExpect(jsonPath("$.valor").value(3000))
                .andExpect(jsonPath("$.valorPrevisto").value(3218.53))
                .andExpect(jsonPath("$.previstoPor").value("MEDIA_3M"))
                .andExpect(jsonPath("$.confirmacoesNaPrevisao").value(3))
                .andExpect(jsonPath("$.basePrevisao.origem").value("FAMILIA"))
                .andExpect(jsonPath("$.basePrevisao.meses", hasSize(3)))
                .andExpect(jsonPath("$.basePrevisao.meses[2].competencia").value("2026-09"))
                .andExpect(jsonPath("$.basePrevisao.meses[2].valor").value(1511.61));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Outras despesas fixas')].categorias[?(@.nome == 'Tarifas bancárias')].previsto[9]")
                        .value(3218.53))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Outras despesas fixas')].categorias[?(@.nome == 'Tarifas bancárias')].previsto[11]")
                        .value(3218.53));

        // Mudar a regra recalcula na hora (nada fica gravado).
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"MAXIMO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/{id}", das)).andExpect(jsonPath("$.valorPrevisto").value(4959.84));
        // Regra própria da conta vale acima da família; nula volta a seguir.
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MINIMO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativa").value("MINIMO"))
                .andExpect(jsonPath("$.valorPrevisto").value(1511.61))
                .andExpect(jsonPath("$.basePrevisao.origem").value("DESPESA"));
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": null}"))
                .andExpect(jsonPath("$.estimativa").doesNotExist())
                .andExpect(jsonPath("$.valorPrevisto").value(4959.84));
        // A regra da categoria vale acima da família (e a conta sem regra própria a segue).
        Long tarifas = categoria("Outras despesas fixas", "Tarifas bancárias");
        mvc.perform(put("/api/categorias/{id}/estimativa", tarifas).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MINIMO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/{id}", das))
                .andExpect(jsonPath("$.valorPrevisto").value(1511.61))
                .andExpect(jsonPath("$.basePrevisao.origem").value("CATEGORIA"));
        mvc.perform(put("/api/categorias/{id}/estimativa", tarifas).contentType(APPLICATION_JSON)
                .content("{\"estimativa\": null}")).andExpect(status().isOk());
        // Confirmar sem informar o valor usa o previsto pela regra.
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-10/confirmacao", das))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(4959.84));

        // Reajuste do valor cadastrado: as confirmações de antes deixam de contar e vale o novo valor.
        mvc.perform(put("/api/recorrencias/{id}", das).contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "DAS", "valor": 3500, "dia": 20, "frequencia": "MENSAL",
                         "inicio": "2026-07"}""".formatted(categoria("Outras despesas fixas", "Tarifas bancárias"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorPrevisto").value(3500))
                .andExpect(jsonPath("$.previstoPor").doesNotExist());

        // Categoria sem estimativa: vale o cadastrado.
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"NENHUMA\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/{id}", das)).andExpect(jsonPath("$.valorPrevisto").value(3500));
    }

    @Test
    void regraDaContaParaTodosOsMesesOuAPartirDeUmMes() throws Exception {
        Long tarifas = categoria("Outras despesas fixas", "Tarifas bancárias");
        Long das = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "DAS", "valor": 3000, "dia": 20, "frequencia": "MENSAL",
                 "inicio": "2026-07"}""".formatted(tarifas));
        for (String[] c : new String[][]{{"2026-07", "3000"}, {"2026-08", "3300"}, {"2026-09", "3600"}}) {
            mvc.perform(post("/api/recorrencias/{id}/ocorrencias/{c}/confirmacao", das, c[0])
                    .contentType(APPLICATION_JSON).content("{\"valor\": %s}".formatted(c[1])))
                    .andExpect(status().isCreated());
        }
        for (String[] c : new String[][]{{"2026-11", "5000"}, {"2027-01", "4000"}}) {
            mvc.perform(put("/api/recorrencias/{id}/ocorrencias/{c}/valor", das, c[0])
                    .contentType(APPLICATION_JSON).content("{\"valor\": %s}".formatted(c[1])))
                    .andExpect(status().isOk());
        }
        String prevista = "$.despesas.grupos[?(@.nome == 'Outras despesas fixas')].categorias[?(@.nome == 'Tarifas bancárias')].previsto[%d]";

        // Sem mês: vale para todos os meses previstos (inclusive do ano que vem) e os ajustados à mão ficam.
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MAXIMO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativaDesde").doesNotExist())
                .andExpect(jsonPath("$.previsoes", hasSize(1)))
                .andExpect(jsonPath("$.previsoes[0].regra").value("MAXIMO"))
                .andExpect(jsonPath("$.previsoes[0].desde").value("2026-10"))
                .andExpect(jsonPath("$.valoresAjustados", hasSize(2)));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath(prevista.formatted(10)).value(5000.0))
                .andExpect(jsonPath(prevista.formatted(11)).value(3600.0));
        mvc.perform(get("/api/visao-anual/2027"))
                .andExpect(jsonPath(prevista.formatted(0)).value(4000.0))
                .andExpect(jsonPath(prevista.formatted(1)).value(3600.0));

        // A partir de dezembro: novembro continua como estava; os ajustados de dezembro em diante seguem a regra nova.
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MINIMO\", \"desde\": \"2026-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativa").value("MINIMO"))
                .andExpect(jsonPath("$.estimativaDesde").value("2026-12"))
                .andExpect(jsonPath("$.estimativaAntes").value("MAXIMO"))
                .andExpect(jsonPath("$.previsoes", hasSize(2)))
                .andExpect(jsonPath("$.previsoes[0].valor").value(3600))
                .andExpect(jsonPath("$.previsoes[1].desde").value("2026-12"))
                .andExpect(jsonPath("$.previsoes[1].valor").value(3000))
                .andExpect(jsonPath("$.valoresAjustados", hasSize(1)))
                .andExpect(jsonPath("$.valoresAjustados[0].competencia").value("2026-11"));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath(prevista.formatted(9)).value(3600.0))
                .andExpect(jsonPath(prevista.formatted(10)).value(5000.0))
                .andExpect(jsonPath(prevista.formatted(11)).value(3000.0));
        mvc.perform(get("/api/visao-anual/2027")).andExpect(jsonPath(prevista.formatted(0)).value(3000.0));

        // A regra da categoria a partir de um mês também leva os ajustados das contas que a seguem.
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                .content("{\"estimativa\": null}")).andExpect(status().isOk());
        mvc.perform(put("/api/categorias/{id}/estimativa", tarifas).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MEDIA\", \"desde\": \"2026-11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativaDesde").value("2026-11"))
                .andExpect(jsonPath("$.estimativaAntes").doesNotExist());
        mvc.perform(get("/api/recorrencias/{id}", das))
                .andExpect(jsonPath("$.valoresAjustados", hasSize(0)))
                .andExpect(jsonPath("$.previsoes", hasSize(2)))
                .andExpect(jsonPath("$.previsoes[0].origem").value("FAMILIA"))
                .andExpect(jsonPath("$.previsoes[1].origem").value("CATEGORIA"))
                .andExpect(jsonPath("$.previsoes[1].valor").value(3300));

        // Mudar a categoria substitui a regra própria das contas dela.
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                .content("{\"estimativa\": \"MAXIMO\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/categorias/{id}/estimativa", tarifas).contentType(APPLICATION_JSON)
                .content("{\"estimativa\": \"MINIMO\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/{id}", das))
                .andExpect(jsonPath("$.estimativa").doesNotExist())
                .andExpect(jsonPath("$.previsoes", hasSize(1)))
                .andExpect(jsonPath("$.previsoes[0].origem").value("CATEGORIA"))
                .andExpect(jsonPath("$.previsoes[0].valor").value(3000));

        // O mês tem que ser o atual ou um à frente.
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MINIMO\", \"desde\": \"2026-09\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/recorrencias/{id}/estimativa", das).contentType(APPLICATION_JSON)
                        .content("{\"estimativa\": \"MINIMO\", \"desde\": \"2026-13\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mesAntesDoInicioEntraNoHistoricoDaConta() throws Exception {
        Long moto = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Vivo - Moto", "valor": 35, "dia": 1, "frequencia": "MENSAL",
                 "inicio": "2026-10"}""".formatted(categoria("Outras despesas fixas", "Tarifas bancárias")));
        mvc.perform(put("/api/recorrencias/{id}/estimativa", moto).contentType(APPLICATION_JSON)
                .content("{\"estimativa\": \"MAXIMO\"}")).andExpect(status().isOk());
        // Setembro é antes do início: vira lançamento ligado à conta e entra na base da previsão.
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", moto)
                        .contentType(APPLICATION_JSON).content("{\"valor\": 40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Vivo - Moto"))
                .andExpect(jsonPath("$.data").value("2026-09-01"));
        mvc.perform(get("/api/recorrencias/{id}", moto))
                .andExpect(jsonPath("$.inicio").value("2026-10"))
                .andExpect(jsonPath("$.valorPrevisto").value(40))
                .andExpect(jsonPath("$.previsoes[0].meses[0].competencia").value("2026-09"));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-09/confirmacao", moto))
                .andExpect(status().isConflict());
        // Confirmação do mês atual e paga adiantada (dezembro) também entram na base.
        for (String[] c : new String[][]{{"2026-10", "50"}, {"2026-12", "60"}}) {
            mvc.perform(post("/api/recorrencias/{id}/ocorrencias/{c}/confirmacao", moto, c[0])
                    .contentType(APPLICATION_JSON).content("{\"valor\": %s}".formatted(c[1])))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/recorrencias/{id}", moto)).andExpect(jsonPath("$.valorPrevisto").value(60));
        mvc.perform(get("/api/visao-anual/2027"))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Outras despesas fixas')].categorias[?(@.nome == 'Tarifas bancárias')].previsto[0]")
                        .value(60.0));
        // Antes do início, só um mês que já chegou.
        Long seguro = postarERetornarId("/api/recorrencias", """
                {"categoriaId": %d, "descricao": "Seguro", "valor": 90, "dia": 5, "frequencia": "MENSAL",
                 "inicio": "2027-01"}""".formatted(categoria("Outras despesas fixas", "Tarifas bancárias")));
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-12/confirmacao", seguro))
                .andExpect(status().isUnprocessableEntity());
    }
}
