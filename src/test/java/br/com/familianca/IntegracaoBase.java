package br.com.familianca;

import com.jayway.jsonpath.JsonPath;
import br.com.familianca.adapter.in.seguranca.LimiteRequisicoesApi;
import br.com.familianca.adapter.in.seguranca.LimiteTentativasLogin;
import br.com.familianca.adapter.in.seguranca.UsuarioAutenticado;
import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.PlanoContasRepository;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.model.Assinatura;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Papel;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.StatusAssinatura;
import br.com.familianca.domain.model.Usuario;
import br.com.familianca.domain.service.CatalogoCategorias;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada teste começa com o banco vazio e uma família (plano PREMIUM ativo, categorias padrão) cujo DONO está
 * autenticado em toda requisição do {@link #mvc} (com token CSRF) e no contexto de segurança da thread. Outros
 * usuários: {@link #mvcComo}. Testes de segurança usam {@link #mvcSemUsuario}, sem autenticação nem CSRF.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
public abstract class IntegracaoBase {

    protected static final String SENHA_PADRAO = "senha-forte-de-teste";

    protected static final LocalDate HOJE_PADRAO = LocalDate.of(2026, 10, 17);

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RelogioAjustavel relogio;

    @Autowired
    protected UsuarioRepository usuarios;

    @Autowired
    protected FamiliaRepository familias;

    @Autowired
    protected MembroRepository membros;

    @Autowired
    private PlanoContasRepository planoContas;

    @Autowired
    private PasswordEncoder senhas;

    @Autowired
    private LimiteTentativasLogin limite;

    @Autowired
    private LimiteRequisicoesApi limiteApi;

    protected MockMvc mvc;

    protected MockMvc mvcSemUsuario;

    /** DONO da família usada pelo {@link #mvc}. */
    protected Membro dono;

    @BeforeEach
    void prepararAmbiente() {
        jdbc.execute("""
                truncate table movimento_investimento, investimento, saldo_mes, lancamento, recorrencia_excecao,
                recorrencia, categoria, grupo_categoria, convite, membro, familia, mfa_codigo_recuperacao, mfa, usuario,
                spring_session, registro_acesso
                restart identity cascade""");
        limite.liberarTodos();
        limiteApi.liberarTodos();
        relogio.definir(HOJE_PADRAO);
        dono = criarFamilia("dono@teste.com", Plano.PREMIUM);
        UsuarioAutenticado principal = principal(dono);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        mvc = mvcComo(dono);
        mvcSemUsuario = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @AfterEach
    void limparContextoSeguranca() {
        SecurityContextHolder.clearContext();
    }

    /** BCrypt 12 leva ~0,3 s: calculado uma vez e reaproveitado por todos os usuários de teste. */
    private static String hashSenhaPadrao;

    protected Usuario criarUsuario(String email, Papel papel) {
        if (hashSenhaPadrao == null) {
            hashSenhaPadrao = senhas.encode(SENHA_PADRAO);
        }
        return usuarios.salvar(Usuario.novo("Usuário " + email.substring(0, email.indexOf('@')), email,
                hashSenhaPadrao, papel));
    }

    /**
     * Mês do saldo inicial das famílias de teste: depois de todos os anos testados, para não levar sobra a nenhum
     * deles (cada ano começa com a sobra de dezembro do anterior, a partir do primeiro saldo).
     */
    protected static final String COMPETENCIA_SALDO_INICIAL = "2099-12";

    /**
     * Família com assinatura ativa no plano, categorias padrão, o usuário como DONO e o saldo inicial (zero) já
     * informado, porque sem ele nada se lança. Para testar a família sem saldo: {@link #semSaldoInicial}.
     */
    protected Membro criarFamilia(String emailDono, Plano plano) {
        Usuario usuario = criarUsuario(emailDono, Papel.USER);
        Familia familia = familias.salvar(Familia.nova("Família " + emailDono,
                new Assinatura(plano, StatusAssinatura.ATIVA, null)));
        planoContas.criarModelo(familia.id(), CatalogoCategorias.padrao());
        jdbc.update("insert into saldo_mes (familia_id, competencia, valor) values (?, ?, 0)", familia.id(),
                COMPETENCIA_SALDO_INICIAL);
        return membros.salvar(Membro.novo(familia.id(), usuario.id(), PapelFamilia.DONO));
    }

    protected void semSaldoInicial(Membro daFamilia) {
        jdbc.update("delete from saldo_mes where familia_id = ?", daFamilia.familiaId());
    }

    protected Membro adicionarMembro(Membro daFamilia, String email, PapelFamilia papel) {
        Usuario usuario = criarUsuario(email, Papel.USER);
        return membros.salvar(Membro.novo(daFamilia.familiaId(), usuario.id(), papel));
    }

    protected UsuarioAutenticado principal(Membro m) {
        Usuario u = usuarios.buscarPorId(m.usuarioId()).orElseThrow();
        UsuarioAutenticado principal = UsuarioAutenticado.de(u, membros.buscar(m.familiaId(), m.id()).orElseThrow());
        principal.eraseCredentials();
        return principal;
    }

    protected MockMvc mvcComo(Membro m) {
        return MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(principal(m))).with(csrf()))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    protected Long postarERetornarId(String url, String json) throws Exception {
        return postarERetornarId(mvc, url, json);
    }

    protected Long postarERetornarId(MockMvc como, String url, String json) throws Exception {
        String corpo = como.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corpo, "$.id")).longValue();
    }

    /** Categoria padrão da família do membro, pelo grupo e nome (há "Outros" em vários grupos). */
    protected Long categoria(Membro daFamilia, String grupo, String nome) {
        return jdbc.queryForObject("""
                select c.id from categoria c join grupo_categoria g on g.id = c.grupo_id
                where c.familia_id = ? and g.nome = ? and c.nome = ?""", Long.class,
                daFamilia.familiaId(), grupo, nome);
    }

    protected Long categoria(String grupo, String nome) {
        return categoria(dono, grupo, nome);
    }

    protected Long grupo(String nome) {
        return jdbc.queryForObject("select id from grupo_categoria where familia_id = ? and nome = ?", Long.class,
                dono.familiaId(), nome);
    }

    protected Long lancar(String grupo, String categoria, String descricao, String valor, String data)
            throws Exception {
        return postarERetornarId("/api/lancamentos", """
                {"categoriaId": %d, "descricao": "%s", "valor": %s, "data": "%s"}"""
                .formatted(categoria(grupo, categoria), descricao, valor, data));
    }
}
