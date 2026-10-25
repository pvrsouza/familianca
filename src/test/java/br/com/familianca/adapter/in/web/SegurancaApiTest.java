package br.com.familianca.adapter.in.web;

import com.jayway.jsonpath.JsonPath;
import br.com.familianca.IntegracaoBase;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.service.Totp;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança de ponta a ponta. {@link #web} roda com o Spring Session JDBC e o Spring Security de verdade: sessão no
 * banco, cookie SESSION e token CSRF obtidos como o front obtém.
 */
class SegurancaApiTest extends IntegracaoBase {

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    SessionRepositoryFilter<?> filtroSessao;

    @Autowired
    FilterChainProxy filtrosSeguranca;

    @Autowired
    CsrfTokenRepository repositorioCsrf;

    MockMvc web;

    @BeforeEach
    void prepararNavegador() {
        // Aqui toda requisição diz quem é; o contexto da thread montado pela base vazaria para mvcSemUsuario.
        SecurityContextHolder.clearContext();
        // Mesma ordem do servidor: Spring Session antes do Spring Security.
        web = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(filtroSessao, (Filter) filtrosSeguranca)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    /** Cookies SESSION e XSRF-TOKEN atuais, guardados entre as requisições como o front faz. */
    final class Navegador {
        Cookie sessao;
        Cookie xsrf;

        MockHttpServletResponse enviar(MockHttpServletRequestBuilder req) throws Exception {
            // O csrf() do spring-security-test troca, de vez, o repositório do CsrfFilter (compartilhado) por um em
            // sessão; o navegador usa o fluxo real (cookie XSRF-TOKEN), então o repositório da aplicação volta.
            WebTestUtils.setCsrfTokenRepository(new MockHttpServletRequest(contexto.getServletContext()), repositorioCsrf);
            if (sessao != null) {
                req.cookie(sessao);
            }
            if (xsrf != null) {
                req.cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue());
            }
            MockHttpServletResponse resposta = web.perform(req).andReturn().getResponse();
            Cookie novaSessao = ultimo(resposta, "SESSION");
            if (novaSessao != null) {
                sessao = novaSessao.getMaxAge() == 0 ? null : novaSessao;
            }
            Cookie novoXsrf = ultimo(resposta, "XSRF-TOKEN");
            if (novoXsrf != null) {
                xsrf = novoXsrf.getMaxAge() == 0 ? null : novoXsrf;
            }
            return resposta;
        }

        /** Como o navegador: com dois Set-Cookie do mesmo nome (ex.: apaga e grava o novo token), vale o último. */
        private static Cookie ultimo(MockHttpServletResponse resposta, String nome) {
            Cookie ultimo = null;
            for (Cookie c : resposta.getCookies()) {
                if (c.getName().equals(nome)) {
                    ultimo = c;
                }
            }
            return ultimo;
        }

        MockHttpServletResponse csrf() throws Exception {
            return enviar(get("/api/auth/csrf"));
        }

        MockHttpServletResponse login(String email, String senha) throws Exception {
            if (xsrf == null) {
                csrf();
            }
            return enviar(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "%s", "senha": "%s"}""".formatted(email, senha)));
        }

        MockHttpServletResponse login(String email, String senha, String codigoMfa) throws Exception {
            if (xsrf == null) {
                csrf();
            }
            return enviar(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"email": "%s", "senha": "%s", "codigoMfa": "%s"}""".formatted(email, senha, codigoMfa)));
        }

        MockHttpServletResponse postar(String url, String json) throws Exception {
            return enviar(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
        }
    }

    @Test
    void anonimoNaoAcessaApi() throws Exception {
        mvcSemUsuario.perform(get("/api/lancamentos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Não autenticado"));
        mvcSemUsuario.perform(get("/api/visao-anual")).andExpect(status().isUnauthorized());
        mvcSemUsuario.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvcSemUsuario.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvcSemUsuario.perform(get("/qualquer-coisa")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotasDoFrontDevolvemOIndexSemExporDados() throws Exception {
        for (String rota : new String[] {"/entrar", "/convite/abc123", "/ano", "/ano/2026", "/lancamentos", "/conta"}) {
            mvcSemUsuario.perform(get(rota)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
        // Fora da lista do SpaController continua negado, mesmo parecido com uma rota do front.
        mvcSemUsuario.perform(get("/ano/2026/extra")).andExpect(status().isUnauthorized());
        mvcSemUsuario.perform(post("/entrar")).andExpect(status().isForbidden());
    }

    @Test
    void actuatorExpoeSoHealthEHeadersDeSeguranca() throws Exception {
        mvcSemUsuario.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
        mvcSemUsuario.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
    }

    @Test
    void cadastroLoginESessaoComAFamilia() throws Exception {
        Navegador nav = new Navegador();
        nav.csrf();
        MockHttpServletResponse cadastro = nav.postar("/api/cadastro", """
                {"nome": "Ana", "email": "ana@teste.com", "senha": "senha-da-ana-123", "nomeFamilia": "Casa da Ana"}""");
        assertThat(cadastro.getStatus()).isEqualTo(201);
        // Cadastro não abre sessão: entra pelo login.
        assertThat(nav.enviar(get("/api/auth/me")).getStatus()).isEqualTo(401);

        MockHttpServletResponse login = nav.login("Ana@Teste.com", "senha-da-ana-123");
        assertThat(login.getStatus()).isEqualTo(200);
        assertThat(login.getContentAsString()).doesNotContain("senha", "$2a$");
        assertThat(nav.sessao.isHttpOnly()).isTrue();
        assertThat(login.getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("SESSION=") && c.contains("SameSite=Strict"));

        String me = nav.enviar(get("/api/auth/me")).getContentAsString();
        assertThat(JsonPath.<String>read(me, "$.papelFamilia")).isEqualTo("DONO");
        assertThat(JsonPath.<String>read(me, "$.papel")).isEqualTo("USER");
        Number familiaId = JsonPath.read(me, "$.familiaId");
        assertThat(familiaId.longValue()).isNotEqualTo(dono.familiaId());

        MockHttpServletResponse familia = nav.enviar(get("/api/familia"));
        assertThat(JsonPath.<String>read(familia.getContentAsString(), "$.nome")).isEqualTo("Casa da Ana");
        assertThat(nav.postar("/api/lancamentos", """
                {"categoriaId": %d, "descricao": "Pão", "valor": 12, "data": "2026-10-17"}"""
                .formatted(categoria(dono, "Habitação", "Supermercado"))).getStatus())
                .as("categoria de outra família").isEqualTo(404);
    }

    @Test
    void loginCompletoGravaORegistroDeAcessoComIpEPortaDeOrigem() throws Exception {
        assertThat(new Navegador().login("dono@teste.com", "senha-errada-123").getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from registro_acesso", Integer.class))
                .as("tentativa errada não é acesso").isZero();

        Navegador nav = new Navegador();
        nav.csrf();
        assertThat(nav.enviar(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Porta-Origem", "54321").content("""
                        {"email": "dono@teste.com", "senha": "%s"}""".formatted(SENHA_PADRAO))).getStatus())
                .isEqualTo(200);
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO).getStatus()).isEqualTo(200);

        List<Map<String, Object>> registros = jdbc.queryForList(
                "select usuario_id, ip, porta, momento from registro_acesso order by id");
        assertThat(registros).hasSize(2);
        assertThat(registros.getFirst()).containsEntry("usuario_id", dono.usuarioId()).containsEntry("ip", "127.0.0.1")
                .containsEntry("porta", 54321);
        assertThat(registros.get(1).get("porta")).as("sem o header do proxy").isNull();
    }

    @Test
    void semTokenCsrfAlteracaoEhNegada() throws Exception {
        Navegador nav = new Navegador();
        nav.login("dono@teste.com", SENHA_PADRAO);
        Cookie xsrf = nav.xsrf;
        nav.xsrf = null;
        assertThat(nav.postar("/api/familia/convites", "{\"email\": \"x@teste.com\"}").getStatus()).isEqualTo(403);
        nav.xsrf = xsrf;
        assertThat(nav.postar("/api/familia/convites", "{\"email\": \"x@teste.com\"}").getStatus()).isEqualTo(201);

        // Cadastro público também exige o token.
        assertThat(new Navegador().enviar(post("/api/cadastro").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"X\", \"email\": \"y@teste.com\", \"senha\": \"senha-qualquer-123\"}"))
                .getStatus()).isEqualTo(403);
    }

    @Test
    void loginComFalhaELimiteDeTentativas() throws Exception {
        String errada = new Navegador().login("dono@teste.com", "senha-errada-123").getContentAsString();
        String inexistente = new Navegador().login("ninguem@teste.com", SENHA_PADRAO).getContentAsString();
        assertThat(JsonPath.<String>read(errada, "$.detail")).isEqualTo("Credenciais inválidas");
        assertThat(inexistente).isEqualTo(errada);

        Navegador nav = new Navegador();
        for (int i = 0; i < 4; i++) {
            assertThat(nav.login("dono@teste.com", "senha-errada-123").getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse bloqueado = nav.login("dono@teste.com", SENHA_PADRAO);
        assertThat(bloqueado.getStatus()).isEqualTo(429);
        assertThat(bloqueado.getHeader("Retry-After")).isEqualTo("900");
    }

    @Test
    void membroRemovidoPerdeASessaoENaoEntraMais() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        Navegador daFilha = new Navegador();
        assertThat(daFilha.login("filha@teste.com", SENHA_PADRAO).getStatus()).isEqualTo(200);
        assertThat(daFilha.enviar(get("/api/lancamentos")).getStatus()).isEqualTo(200);

        Navegador doDono = new Navegador();
        doDono.login("dono@teste.com", SENHA_PADRAO);
        assertThat(doDono.enviar(delete("/api/familia/membros/{id}", filha.id())).getStatus()).isEqualTo(204);

        assertThat(daFilha.enviar(get("/api/lancamentos")).getStatus()).isEqualTo(401);
        assertThat(new Navegador().login("filha@teste.com", SENHA_PADRAO).getStatus()).isEqualTo(401);
    }

    @Test
    void mudarOPapelDerrubaAsSessoesDoMembro() throws Exception {
        Membro filha = adicionarMembro(dono, "filha@teste.com", PapelFamilia.MEMBRO);
        Navegador daFilha = new Navegador();
        daFilha.login("filha@teste.com", SENHA_PADRAO);

        mvc.perform(put("/api/familia/membros/{id}/papel", filha.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"papel\": \"DONO\"}"))
                .andExpect(status().isOk());

        assertThat(daFilha.enviar(get("/api/auth/me")).getStatus()).isEqualTo(401);
        Navegador novoLogin = new Navegador();
        novoLogin.login("filha@teste.com", SENHA_PADRAO);
        String me = novoLogin.enviar(get("/api/auth/me")).getContentAsString();
        assertThat(JsonPath.<String>read(me, "$.papelFamilia")).isEqualTo("DONO");
    }

    @Test
    void logoutInvalidaASessao() throws Exception {
        Navegador nav = new Navegador();
        nav.login("dono@teste.com", SENHA_PADRAO);
        Cookie sessao = nav.sessao;
        assertThat(nav.enviar(post("/api/auth/logout")).getStatus()).isEqualTo(204);

        Navegador comCookieAntigo = new Navegador();
        comCookieAntigo.sessao = sessao;
        assertThat(comCookieAntigo.enviar(get("/api/auth/me")).getStatus()).isEqualTo(401);
    }

    @Test
    void familiasIsoladas() throws Exception {
        Long lancamento = lancar("Habitação", "Água", "Água", "90", "2026-10-01");
        Membro outro = criarFamilia("outro@teste.com", Plano.PREMIUM);
        Navegador doOutro = new Navegador();
        doOutro.login("outro@teste.com", SENHA_PADRAO);

        assertThat(doOutro.enviar(get("/api/lancamentos/{id}", lancamento)).getStatus()).isEqualTo(404);
        String visao = doOutro.enviar(get("/api/visao-anual/2026")).getContentAsString();
        assertThat(JsonPath.<Number>read(visao, "$.resumo.despesasTotal.ano").intValue()).isZero();
        assertThat(outro.familiaId()).isNotEqualTo(dono.familiaId());
    }

    @Test
    void verificacaoEmDuasEtapas() throws Exception {
        Navegador nav = new Navegador();
        assertThat(nav.login("dono@teste.com", SENHA_PADRAO).getStatus()).isEqualTo(200);
        Navegador outroNavegador = new Navegador();
        outroNavegador.login("dono@teste.com", SENHA_PADRAO);

        assertThat(nav.enviar(get("/api/conta/mfa")).getContentAsString())
                .contains("\"ativo\":false").contains("\"disponivel\":true");
        String config = nav.postar("/api/conta/mfa/configuracao", "{}").getContentAsString();
        String segredo = JsonPath.read(config, "$.segredo");
        assertThat(JsonPath.<String>read(config, "$.uri"))
                .startsWith("otpauth://totp/Familian%C3%A7a:dono%40teste.com?secret=" + segredo);
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO).getStatus())
                .as("configurado, mas ainda não ativado").isEqualTo(200);

        long passo = Totp.passo(relogio.agora().toEpochSecond());
        assertThat(nav.postar("/api/conta/mfa/ativacao", codigo(Totp.codigo(segredo, passo - 5))).getStatus())
                .isEqualTo(422);
        MockHttpServletResponse ativacao = nav.postar("/api/conta/mfa/ativacao", codigo(Totp.codigo(segredo, passo)));
        assertThat(ativacao.getStatus()).isEqualTo(200);
        List<String> recuperacao = JsonPath.read(ativacao.getContentAsString(), "$.codigosRecuperacao");
        assertThat(recuperacao).hasSize(10).allMatch(c -> c.matches("[a-z2-9]{5}-[a-z2-9]{5}"));
        assertThat(jdbc.queryForObject("select segredo_cifrado from mfa", String.class)).doesNotContain(segredo);
        assertThat(jdbc.queryForList("select codigo_hash from mfa_codigo_recuperacao", String.class))
                .hasSize(10).noneMatch(recuperacao::contains);
        assertThat(nav.enviar(get("/api/conta/mfa")).getContentAsString()).contains("\"ativo\":true");
        assertThat(outroNavegador.enviar(get("/api/auth/me")).getStatus()).as("sessão aberta sem MFA cai").isEqualTo(401);

        MockHttpServletResponse semCodigo = new Navegador().login("dono@teste.com", SENHA_PADRAO);
        assertThat(semCodigo.getStatus()).isEqualTo(401);
        assertThat(JsonPath.<Boolean>read(semCodigo.getContentAsString(), "$.mfaObrigatorio")).isTrue();
        assertThat(new Navegador().login("dono@teste.com", "senha-errada-123").getContentAsString())
                .as("senha errada não revela que há MFA").doesNotContain("mfaObrigatorio");
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO, Totp.codigo(segredo, passo)).getStatus())
                .as("código já usado na ativação").isEqualTo(401);
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO, Totp.codigo(segredo, passo + 1)).getStatus())
                .isEqualTo(200);

        String deRecuperacao = recuperacao.getFirst().toUpperCase();
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO, deRecuperacao).getStatus()).isEqualTo(200);
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO, deRecuperacao).getStatus())
                .as("código de recuperação vale uma vez").isEqualTo(401);

        assertThat(nav.postar("/api/conta/mfa/desativacao", """
                {"senha": "senha-errada-123", "codigo": "%s"}""".formatted(recuperacao.get(1))).getStatus())
                .isEqualTo(422);
        assertThat(nav.postar("/api/conta/mfa/desativacao", """
                {"senha": "%s", "codigo": "%s"}""".formatted(SENHA_PADRAO, recuperacao.get(1))).getStatus())
                .isEqualTo(204);
        assertThat(new Navegador().login("dono@teste.com", SENHA_PADRAO).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from mfa_codigo_recuperacao", Integer.class)).isZero();
    }

    @Test
    void sessaoExpiraNaDuracaoMaximaMesmoEmUso() throws Exception {
        Navegador nav = new Navegador();
        nav.login("dono@teste.com", SENHA_PADRAO);
        assertThat(nav.enviar(get("/api/auth/me")).getStatus()).isEqualTo(200);

        jdbc.update("update spring_session set creation_time = creation_time - ?", Duration.ofHours(13).toMillis());
        assertThat(nav.enviar(get("/api/auth/me")).getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
    }

    private static String codigo(String codigo) {
        return "{\"codigo\": \"%s\"}".formatted(codigo);
    }
}
