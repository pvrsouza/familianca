package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;

/**
 * Trilha de auditoria no logger "auditoria": login ok/falha/bloqueado, logout e acesso negado, com id do usuário, IP
 * e horário. Nunca registra senha, e-mail digitado, id de sessão, token nem dado pessoal: o usuário aparece só pelo
 * id (ou "desconhecido").
 */
@Component
public class AuditoriaSeguranca {

    private static final Logger log = LoggerFactory.getLogger("auditoria");

    private final UsuarioRepository usuarios;

    public AuditoriaSeguranca(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @EventListener
    public void loginOk(AuthenticationSuccessEvent evento) {
        registrar("LOGIN_OK", idDe(evento.getAuthentication()));
    }

    @EventListener
    public void loginFalhou(AbstractAuthenticationFailureEvent evento) {
        registrar("LOGIN_FALHA", idPorEmail(String.valueOf(evento.getAuthentication().getPrincipal())));
    }

    public void loginBloqueado(String email) {
        registrar("LOGIN_BLOQUEADO", idPorEmail(email));
    }

    /** Senha certa, código do segundo fator errado (o LOGIN_OK da senha já foi registrado antes). */
    public void segundoFatorRecusado(Long usuarioId) {
        registrar("MFA_FALHA", String.valueOf(usuarioId));
    }

    @EventListener
    public void logout(LogoutSuccessEvent evento) {
        registrar("LOGOUT", idDe(evento.getAuthentication()));
    }

    @EventListener
    public void acessoNegado(AuthorizationDeniedEvent<?> evento) {
        Authentication auth = evento.getAuthentication().get();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            // Anônimo recebe 401 (não autenticado); não polui a trilha com varreduras.
            log.debug("evento=NAO_AUTENTICADO ip={} recurso={}", ip(), recurso());
            return;
        }
        log.warn("evento=ACESSO_NEGADO usuarioId={} ip={} recurso={} em={}", idDe(auth), ip(), recurso(),
                OffsetDateTime.now());
    }

    private void registrar(String evento, String usuarioId) {
        log.info("evento={} usuarioId={} ip={} em={}", evento, usuarioId, ip(), OffsetDateTime.now());
    }

    private static String idDe(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u ? String.valueOf(u.id()) : "desconhecido";
    }

    private String idPorEmail(String email) {
        return usuarios.buscarPorEmail(Usuario.normalizarEmail(email))
                .map(u -> String.valueOf(u.id())).orElse("desconhecido");
    }

    private static String ip() {
        HttpServletRequest r = requisicao();
        return r != null ? r.getRemoteAddr() : "-";
    }

    /** Método e rota, sem query string (pode conter dados). */
    private static String recurso() {
        HttpServletRequest r = requisicao();
        return r != null ? r.getMethod() + " " + r.getRequestURI() : "-";
    }

    private static HttpServletRequest requisicao() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }
}
