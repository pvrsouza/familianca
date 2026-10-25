package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.seguranca.AuditoriaSeguranca;
import br.com.familianca.adapter.in.seguranca.LimiteTentativasLogin;
import br.com.familianca.adapter.in.seguranca.SessaoConfig;
import br.com.familianca.adapter.in.seguranca.TipoCliente;
import br.com.familianca.adapter.in.seguranca.UsuarioAutenticado;
import br.com.familianca.adapter.in.web.dto.LoginRequest;
import br.com.familianca.adapter.in.web.dto.UsuarioLogadoResponse;
import br.com.familianca.application.port.in.RegistrarAcesso;
import br.com.familianca.application.port.in.VerificarSegundoFator;
import br.com.familianca.domain.model.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** O logout (POST /api/auth/logout) é do próprio Spring Security, configurado em SecurityConfig. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = """
        Login com sessão de servidor: o id de sessão vai num cookie HttpOnly. Toda requisição que altera dados
        (inclusive o login e o logout) precisa do header `X-XSRF-TOKEN` com o valor do cookie `XSRF-TOKEN`, obtido em
        `GET /api/auth/csrf`. A sessão guarda a família e o papel do usuário nela.""")
public class AuthController {

    private final AuthenticationManager autenticacao;
    private final SessionAuthenticationStrategy estrategiaSessao;
    private final SecurityContextRepository contextos;
    private final LimiteTentativasLogin limite;
    private final AuditoriaSeguranca auditoria;
    private final SessaoConfig sessao;
    private final VerificarSegundoFator segundoFator;
    private final RegistrarAcesso registroAcesso;

    public AuthController(AuthenticationManager autenticacao, SessionAuthenticationStrategy estrategiaSessao,
                          SecurityContextRepository contextos, LimiteTentativasLogin limite,
                          AuditoriaSeguranca auditoria, SessaoConfig sessao, VerificarSegundoFator segundoFator,
                          RegistrarAcesso registroAcesso) {
        this.autenticacao = autenticacao;
        this.estrategiaSessao = estrategiaSessao;
        this.contextos = contextos;
        this.limite = limite;
        this.auditoria = auditoria;
        this.sessao = sessao;
        this.segundoFator = segundoFator;
        this.registroAcesso = registroAcesso;
    }

    @GetMapping("/csrf")
    @Operation(summary = "Token CSRF", description = "Grava o cookie `XSRF-TOKEN` e devolve o header que deve repeti-lo.")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar", description = """
            Autentica por e-mail e senha e abre a sessão (novo id de sessão a cada login). Erro de e-mail ou senha
            responde sempre 401 "Credenciais inválidas", sem dizer se o usuário existe; membro removido da família
            também. Mais de 5 tentativas em 15 minutos para o mesmo e-mail e IP: 429.

            Com verificação em duas etapas ativa, envie também `codigoMfa`. Sem ele (senha certa): 401 com
            `mfaObrigatorio: true`, para o front pedir o código e repetir o login. Código errado: 401 "Credenciais
            inválidas" e conta no limite de tentativas.""")
    @ApiResponse(responseCode = "200", description = "Login feito; cookie de sessão gravado")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas ou código de verificação necessário")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    public UsuarioLogadoResponse login(@Valid @RequestBody LoginRequest corpo, HttpServletRequest request,
                                       HttpServletResponse response) {
        String email = Usuario.normalizarEmail(corpo.email());
        String ip = request.getRemoteAddr();
        if (!limite.tentar(ip, email)) {
            auditoria.loginBloqueado(email);
            throw new TentativasExcedidasException(limite.segundosParaNovaTentativa());
        }
        Authentication autenticado;
        try {
            autenticado = autenticacao.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, corpo.senha()));
        } catch (AuthenticationException e) {
            // Usuário inexistente, senha errada e usuário/membro inativo: mesma resposta.
            throw new BadCredentialsException("Credenciais inválidas");
        }
        exigirSegundoFator((UsuarioAutenticado) autenticado.getPrincipal(), corpo.codigoMfa());
        limite.liberar(ip, email);
        registroAcesso.registrar(((UsuarioAutenticado) autenticado.getPrincipal()).id(), ip, portaOrigem(request));

        estrategiaSessao.onAuthentication(autenticado, request, response);
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        sessao.aplicarTimeout(request.getSession(), TipoCliente.WEB);
        // O token CSRF foi trocado no login: carregar o novo grava o cookie já nesta resposta.
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken novo) {
            novo.getToken();
        }
        return UsuarioLogadoResponse.de((UsuarioAutenticado) autenticado.getPrincipal());
    }

    /**
     * Porta de origem do cliente, posta pelo Caddy em {@code X-Porta-Origem} (sobrescreve a que vier do cliente; o app
     * não publica porta). Sem o header ou com valor inválido, nula.
     */
    private static Integer portaOrigem(HttpServletRequest request) {
        try {
            int porta = Integer.parseInt(request.getHeader("X-Porta-Origem"));
            return porta >= 1 && porta <= 65535 ? porta : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Antes de abrir a sessão: sem o código certo, a senha correta não basta. */
    private void exigirSegundoFator(UsuarioAutenticado usuario, String codigo) {
        if (!segundoFator.exigido(usuario.id())) {
            return;
        }
        if (codigo == null || codigo.isBlank()) {
            throw new MfaObrigatorioException();
        }
        if (!segundoFator.confere(usuario.id(), codigo)) {
            auditoria.segundoFatorRecusado(usuario.id());
            throw new BadCredentialsException("Credenciais inválidas");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário logado", description = "Inclui a família e o papel nela. 401 se não há sessão válida.")
    public UsuarioLogadoResponse me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return UsuarioLogadoResponse.de(usuario);
    }
}
