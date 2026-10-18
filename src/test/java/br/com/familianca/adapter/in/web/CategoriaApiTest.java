package br.com.familianca.adapter.in.web;

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

class CategoriaApiTest extends IntegracaoBase {

    @Test
    void planoDeContasPadraoEmArvore() throws Exception {
        mvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupos", hasSize(10)))
                .andExpect(jsonPath("$.grupos[0].tipo").value("RECEITA"))
                .andExpect(jsonPath("$.grupos[0].nome").value("Receitas fixas"))
                .andExpect(jsonPath("$.grupos[0].categorias[0].nome").value("Salário"))
                .andExpect(jsonPath("$.grupos[2].nome").value("Habitação"))
                .andExpect(jsonPath("$.grupos[2].natureza").value("FIXA"));
    }

    @Test
    void criaEditaDesativaEExclui() throws Exception {
        Long grupoId = postarERetornarId("/api/categorias/grupos", """
                {"tipo": "DESPESA", "natureza": "VARIAVEL", "nome": "Pets"}""");
        mvc.perform(post("/api/categorias/grupos").contentType(APPLICATION_JSON).content("""
                        {"tipo": "DESPESA", "natureza": "FIXA", "nome": "pets"}"""))
                .andExpect(status().isConflict());
        Long racaoId = postarERetornarId("/api/categorias", """
                {"grupoId": %d, "nome": "Ração"}""".formatted(grupoId));
        mvc.perform(post("/api/categorias").contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"RAÇÃO\"}".formatted(grupoId)))
                .andExpect(status().isConflict());

        // Mover para grupo de outro tipo não pode.
        mvc.perform(put("/api/categorias/{id}", racaoId).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Ração\"}".formatted(grupo("Receitas fixas"))))
                .andExpect(status().isUnprocessableEntity());

        postarERetornarId("/api/lancamentos", """
                {"categoriaId": %d, "descricao": "Ração", "valor": 150, "data": "2026-10-01"}""".formatted(racaoId));
        mvc.perform(delete("/api/categorias/{id}", racaoId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/categorias/grupos/{id}", grupoId)).andExpect(status().isConflict());

        // Desativada: some dos novos lançamentos, o histórico fica.
        mvc.perform(put("/api/categorias/{id}", racaoId).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Ração\", \"ativa\": false}".formatted(grupoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "Ração", "valor": 150, "data": "2026-10-02"}""".formatted(racaoId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A categoria Ração está desativada"));

        Long semUso = postarERetornarId("/api/categorias", "{\"grupoId\": %d, \"nome\": \"Vacinas\"}".formatted(grupoId));
        mvc.perform(delete("/api/categorias/{id}", semUso)).andExpect(status().isNoContent());
        Long grupoVazio = postarERetornarId("/api/categorias/grupos", """
                {"tipo": "RECEITA", "natureza": "VARIAVEL", "nome": "Prêmios"}""");
        mvc.perform(delete("/api/categorias/grupos/{id}", grupoVazio)).andExpect(status().isNoContent());
    }

    @Test
    void categoriaRecorrente() throws Exception {
        // O padrão já marca o que costuma se repetir.
        mvc.perform(get("/api/categorias"))
                .andExpect(jsonPath("$.grupos[0].categorias[0].nome").value("Salário"))
                .andExpect(jsonPath("$.grupos[0].categorias[0].recorrente").value(true))
                .andExpect(jsonPath("$.grupos[2].nome").value("Habitação"))
                .andExpect(jsonPath("$.grupos[2].categorias[8].nome").value("Supermercado"))
                .andExpect(jsonPath("$.grupos[2].categorias[8].recorrente").value(false));

        Long grupoId = grupo("Habitação");
        Long id = postarERetornarId("/api/categorias", """
                {"grupoId": %d, "nome": "Seguro residencial", "recorrente": true}""".formatted(grupoId));
        // Sem o campo (cliente antigo), a edição mantém; false desliga.
        mvc.perform(put("/api/categorias/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Seguro da casa\"}".formatted(grupoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorrente").value(true));
        mvc.perform(put("/api/categorias/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Seguro da casa\", \"recorrente\": false}".formatted(grupoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recorrente").value(false));
    }

    @Test
    void estimativaPropriaOuDaFamilia() throws Exception {
        Long grupoId = grupo("Habitação");
        Long id = categoria("Habitação", "Supermercado");
        mvc.perform(get("/api/categorias"))
                .andExpect(jsonPath("$.grupos[2].categorias[8].estimativa").doesNotExist());
        mvc.perform(put("/api/categorias/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Supermercado\", \"estimativa\": \"MAXIMO\"}".formatted(grupoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimativa").value("MAXIMO"));
        // Sem o campo, mantém; FAMILIA volta a seguir a regra da família.
        mvc.perform(put("/api/categorias/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Mercado\"}".formatted(grupoId)))
                .andExpect(jsonPath("$.estimativa").value("MAXIMO"));
        mvc.perform(put("/api/categorias/{id}", id).contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Mercado\", \"estimativa\": \"FAMILIA\"}".formatted(grupoId)))
                .andExpect(jsonPath("$.estimativa").doesNotExist());
    }

    @Test
    void membroNaoAlteraEOutraFamiliaNaoVe() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        mvcComo(filha).perform(get("/api/categorias")).andExpect(status().isOk());
        mvcComo(filha).perform(post("/api/categorias/grupos").contentType(APPLICATION_JSON)
                        .content("{\"tipo\": \"DESPESA\", \"natureza\": \"FIXA\", \"nome\": \"Meu\"}"))
                .andExpect(status().isForbidden());

        Membro outro = criarFamilia("outro@teste.com", Plano.PREMIUM);
        mvcComo(outro).perform(put("/api/categorias/{id}", categoria("Habitação", "Água"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"grupoId\": %d, \"nome\": \"Hackeada\"}".formatted(grupo("Habitação"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void modeloPjDependeDoPlano() throws Exception {
        Membro familia = criarFamilia("familia@teste.com", Plano.FAMILIA);
        mvcComo(familia).perform(post("/api/categorias/modelos/pj"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.recurso").value("DESPESAS_PJ"));
        mvcComo(familia).perform(post("/api/categorias").contentType(APPLICATION_JSON).content("""
                        {"grupoId": %d, "nome": "ISS", "aliquota": 5}""".formatted(
                        jdbc.queryForObject("select id from grupo_categoria where familia_id = ? and nome = 'Habitação'",
                                Long.class, familia.familiaId()))))
                .andExpect(status().isPaymentRequired());

        mvc.perform(post("/api/categorias/modelos/pj"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grupos[?(@.nome == 'Empresa (PJ)')].empresa").value(true));
        mvc.perform(post("/api/categorias/modelos/pj")).andExpect(status().isConflict());

        Long iss = categoria("Empresa (PJ)", "ISS");
        mvc.perform(post("/api/lancamentos").contentType(APPLICATION_JSON).content("""
                        {"categoriaId": %d, "descricao": "ISS", "valor": 10, "data": "2026-10-01"}""".formatted(iss)))
                .andExpect(status().isUnprocessableEntity());
    }
}
