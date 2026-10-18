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
                .andExpect(jsonPath("$.valorNoAno").value(223.60));
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
    void semConfirmacaoPodeExcluir() throws Exception {
        Long id = streaming();
        mvc.perform(delete("/api/recorrencias/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/recorrencias/{id}", id)).andExpect(status().isNotFound());
    }
}
