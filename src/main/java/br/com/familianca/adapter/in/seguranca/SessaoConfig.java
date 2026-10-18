package br.com.familianca.adapter.in.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

/**
 * Onde a sessão do Spring Session JDBC é encontrada na requisição, e por quanto tempo vale. Único ponto a mexer para
 * o app mobile.
 *
 * <p><b>Hoje (web):</b> o id de sessão só trafega no cookie (HttpOnly, SameSite=Strict, Secure por perfil) e toda
 * requisição que altera dados exige o token CSRF.
 *
 * <p><b>Para ativar o mobile:</b>
 * <ol>
 *   <li>{@code familianca.seguranca.sessao.token-header-habilitado=true} (env {@code SESSAO_TOKEN_HEADER=true}): o
 *       resolver passa a ser {@link ResolvedorSessaoCookieOuHeader}, que aceita o id de sessão no header
 *       {@value #HEADER_TOKEN} e, sem ele, continua no cookie;</li>
 *   <li>as requisições com {@value #HEADER_TOKEN} ficam dispensadas de CSRF ({@link #autenticadaPorHeader}): o
 *       navegador nunca envia esse header sozinho, então não há CSRF possível;</li>
 *   <li>falta o fluxo de login do app: identificar o cliente como {@link TipoCliente#MOBILE} (ex.: header
 *       {@code X-Client: mobile} no login), devolver o id de sessão no header da resposta e aplicar
 *       {@link #aplicarTimeout} com MOBILE ({@code familianca.seguranca.sessao.timeout-mobile}).</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class SessaoConfig {

    public static final String HEADER_TOKEN = "X-Auth-Token";

    private final SegurancaProperties.Sessao propriedades;

    public SessaoConfig(SegurancaProperties propriedades) {
        this.propriedades = propriedades.sessao();
    }

    @Bean
    DefaultCookieSerializer cookieSessao() {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName(propriedades.cookieNome());
        cookie.setCookiePath("/");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setUseSecureCookie(propriedades.cookieSeguro());
        cookie.setSameSite("Strict");
        return cookie;
    }

    @Bean
    HttpSessionIdResolver resolvedorSessao(DefaultCookieSerializer cookieSessao) {
        CookieHttpSessionIdResolver porCookie = new CookieHttpSessionIdResolver();
        porCookie.setCookieSerializer(cookieSessao);
        if (!propriedades.tokenHeaderHabilitado()) {
            return porCookie;
        }
        return new ResolvedorSessaoCookieOuHeader(new HeaderHttpSessionIdResolver(HEADER_TOKEN), porCookie);
    }

    /** Requisições que se identificam pelo header (mobile) e por isso dispensam CSRF. Nunca casa com ele desligado. */
    @Bean
    RequestMatcher autenticadaPorHeader() {
        boolean habilitado = propriedades.tokenHeaderHabilitado();
        return (HttpServletRequest r) -> habilitado && r.getHeader(HEADER_TOKEN) != null;
    }

    /** Inatividade máxima da sessão conforme o tipo de cliente. Chamado logo após o login. */
    public void aplicarTimeout(HttpSession sessao, TipoCliente cliente) {
        sessao.setMaxInactiveInterval((int) (cliente == TipoCliente.MOBILE
                ? propriedades.timeoutMobile() : propriedades.timeoutWeb()).toSeconds());
    }
}
