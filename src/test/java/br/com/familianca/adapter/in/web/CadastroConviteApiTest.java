package br.com.familianca.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CadastroConviteApiTest extends IntegracaoBase {

    @Test
    void cadastroCriaAFamiliaEmTesteComCategoriasPadrao() throws Exception {
        String corpo = mvcSemUsuario.perform(post("/api/cadastro").with(csrf()).contentType(APPLICATION_JSON).content("""
                        {"nome": "Paulo Souza", "email": "Paulo@Exemplo.com", "senha": "uma-senha-bem-forte"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("paulo@exemplo.com"))
                .andExpect(jsonPath("$.papel").value("DONO"))
                .andReturn().getResponse().getContentAsString();
        Long familiaId = ((Number) JsonPath.read(corpo, "$.familiaId")).longValue();

        assertThat(jdbc.queryForObject("select nome from familia where id = ?", String.class, familiaId))
                .isEqualTo("Família de Paulo");
        assertThat(jdbc.queryForObject("select plano || ' ' || status_assinatura || ' ' || assinatura_valida_ate "
                + "from familia where id = ?", String.class, familiaId)).isEqualTo("PREMIUM TESTE 2026-11-16");
        assertThat(jdbc.queryForObject("select count(*) from grupo_categoria where familia_id = ?", Integer.class,
                familiaId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select count(*) from categoria where familia_id = ?", Integer.class,
                familiaId)).isGreaterThan(60);

        mvcSemUsuario.perform(post("/api/cadastro").with(csrf()).contentType(APPLICATION_JSON).content("""
                        {"nome": "Outro", "email": "paulo@exemplo.com", "senha": "uma-senha-bem-forte"}"""))
                .andExpect(status().isConflict());
        mvcSemUsuario.perform(post("/api/cadastro").with(csrf()).contentType(APPLICATION_JSON).content("""
                        {"nome": "Curta", "email": "curta@exemplo.com", "senha": "curta"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conviteDoComecoAoFim() throws Exception {
        String corpo = mvc.perform(post("/api/familia/convites").contentType(APPLICATION_JSON)
                        .content("{\"email\": \"Filha@Teste.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("MEMBRO"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.link").value(startsWith("http://localhost:5174/convite/")))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(corpo, "$.token");
        assertThat(jdbc.queryForObject("select token_hash from convite", String.class)).isNotEqualTo(token).hasSize(64);

        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.convitesPendentes", hasSize(1)))
                .andExpect(jsonPath("$.convitesPendentes[0].token").doesNotExist());
        mvcSemUsuario.perform(get("/api/convites/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.familia").value("Família dono@teste.com"))
                .andExpect(jsonPath("$.email").value("filha@teste.com"));

        mvcSemUsuario.perform(post("/api/convites/{token}/aceite", token).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"nome\": \"Filha\", \"senha\": \"senha-da-filha-123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.familiaId").value(dono.familiaId()))
                .andExpect(jsonPath("$.papel").value("MEMBRO"));

        // Uso único.
        mvcSemUsuario.perform(get("/api/convites/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/familia"))
                .andExpect(jsonPath("$.membros", hasSize(2)))
                .andExpect(jsonPath("$.convitesPendentes", hasSize(0)));
    }

    @Test
    void regrasDoConvite() throws Exception {
        mvc.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"a@teste.com\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"a@teste.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe convite pendente para este e-mail"));
        mvc.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"dono@teste.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta pessoa já participa da família"));
        criarFamilia("outra@teste.com", Plano.PREMIUM);
        mvc.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"outra@teste.com\"}"))
                .andExpect(status().isConflict());

        // Expirado: some, e um novo convite para o mesmo e-mail pode ser feito.
        relogio.definir(HOJE_PADRAO.plusDays(8));
        mvc.perform(get("/api/familia")).andExpect(jsonPath("$.convitesPendentes", hasSize(0)));
        mvc.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"a@teste.com\"}"))
                .andExpect(status().isCreated());

        Long conviteId = jdbc.queryForObject("select id from convite where status = 'PENDENTE'", Long.class);
        mvc.perform(delete("/api/familia/convites/{id}", conviteId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/familia/convites/{id}", conviteId)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void limiteDePessoasDoPlano() throws Exception {
        Membro gratuito = criarFamilia("gratuito@teste.com", Plano.GRATUITO);
        var como = mvcComo(gratuito);
        como.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"b@teste.com\"}"))
                .andExpect(status().isCreated());
        como.perform(post("/api/familia/convites").contentType(APPLICATION_JSON).content("{\"email\": \"c@teste.com\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.plano").value("GRATUITO"))
                .andExpect(jsonPath("$.detail").value(
                        "O plano Gratuito permite até 2 pessoas na família (contando os convites pendentes)"));
    }

    @Test
    void membroNaoConvida() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        mvcComo(filha).perform(post("/api/familia/convites").contentType(APPLICATION_JSON)
                        .content("{\"email\": \"x@teste.com\"}"))
                .andExpect(status().isForbidden());
    }
}
