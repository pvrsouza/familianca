package br.com.familianca.adapter.in.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Expiração absoluta: a sessão do navegador vale no máximo {@code duracao-maxima} desde o login (padrão 12 h), mesmo
 * em uso contínuo; a inatividade (30 min) é do Spring Session. Passou do tempo: a sessão é apagada e a requisição
 * segue como anônima (401). A sessão do app mobile (por header) tem regra própria e fica de fora.
 * Fica na cadeia do Spring Security (SecurityConfig), não registrado como filtro do servlet.
 */
final class DuracaoMaximaSessao extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("auditoria");

    private final long maximaMs;
    private final RequestMatcher autenticadaPorHeader;

    DuracaoMaximaSessao(Duration maxima, RequestMatcher autenticadaPorHeader) {
        this.maximaMs = maxima.toMillis();
        this.autenticadaPorHeader = autenticadaPorHeader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession sessao = request.getSession(false);
        if (sessao != null && !autenticadaPorHeader.matches(request)
                && System.currentTimeMillis() - sessao.getCreationTime() > maximaMs) {
            sessao.invalidate();
            log.info("evento=SESSAO_EXPIRADA ip={}", request.getRemoteAddr());
        }
        chain.doFilter(request, response);
    }
}
