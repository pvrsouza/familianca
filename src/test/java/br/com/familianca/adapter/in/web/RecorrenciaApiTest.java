package br.com.familianca.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import br.com.familianca.IntegracaoBase;
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
        mvc.perform(post("/api/recorrencias/{id}/ocorrencias/2026-07/confirmacao", id))
                .andExpect(status().isUnprocessableEntity());

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
        for (String[] c : new String[][]{{"2026-07", "3184.15"}, {"2026-08", "4959.84"}, {"2026-09", "1511.61"}}) {
            mvc.perform(post("/api/recorrencias/{id}/ocorrencias/{c}/confirmacao", das, c[0])
                    .contentType(APPLICATION_JSON).content("{\"valor\": %s}".formatted(c[1])))
                    .andExpect(status().isCreated());
        }
        // Média das três confirmações: 3.218,53, do mês atual em diante.
        mvc.perform(get("/api/recorrencias/{id}", das))
                .andExpect(jsonPath("$.valor").value(3000))
                .andExpect(jsonPath("$.valorPrevisto").value(3218.53))
                .andExpect(jsonPath("$.previstoPor").value("MEDIA"))
                .andExpect(jsonPath("$.confirmacoesNaPrevisao").value(3));
        mvc.perform(get("/api/visao-anual/2026"))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Outras despesas fixas')].categorias[?(@.nome == 'Tarifas bancárias')].previsto[9]")
                        .value(3218.53))
                .andExpect(jsonPath("$.despesas.grupos[?(@.nome == 'Outras despesas fixas')].categorias[?(@.nome == 'Tarifas bancárias')].previsto[11]")
                        .value(3218.53));

        // Mudar a regra recalcula na hora (nada fica gravado).
        mvc.perform(put("/api/familia/estimativa").contentType(APPLICATION_JSON).content("{\"estimativa\": \"MAXIMO\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recorrencias/{id}", das)).andExpect(jsonPath("$.valorPrevisto").value(4959.84));
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
}
