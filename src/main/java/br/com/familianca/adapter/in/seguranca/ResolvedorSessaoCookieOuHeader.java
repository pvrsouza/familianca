package br.com.familianca.adapter.in.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.util.List;

/**
 * App mobile, desligado por padrão: com o header {@value SessaoConfig#HEADER_TOKEN} na requisição, a sessão vem
 * só dele (o cookie é ignorado); sem o header, vem do cookie, como na web. Ver {@link SessaoConfig}.
 */
class ResolvedorSessaoCookieOuHeader implements HttpSessionIdResolver {

    private final HttpSessionIdResolver header;
    private final HttpSessionIdResolver cookie;

    ResolvedorSessaoCookieOuHeader(HttpSessionIdResolver header, HttpSessionIdResolver cookie) {
        this.header = header;
        this.cookie = cookie;
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        return delegado(request).resolveSessionIds(request);
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        delegado(request).setSessionId(request, response, sessionId);
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        delegado(request).expireSession(request, response);
    }

    private HttpSessionIdResolver delegado(HttpServletRequest request) {
        return request.getHeader(SessaoConfig.HEADER_TOKEN) != null ? header : cookie;
    }
}
