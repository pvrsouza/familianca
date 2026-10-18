package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.seguranca.LimiteTentativasLogin;
import br.com.familianca.adapter.in.seguranca.UsuarioAutenticado;
import br.com.familianca.adapter.in.web.dto.CodigoMfaRequest;
import br.com.familianca.adapter.in.web.dto.CodigosRecuperacaoResponse;
import br.com.familianca.adapter.in.web.dto.DesativarMfaRequest;
import br.com.familianca.adapter.in.web.dto.MfaConfiguracaoResponse;
import br.com.familianca.adapter.in.web.dto.MfaSituacaoResponse;
import br.com.familianca.application.port.in.GerenciarMfa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/conta/mfa")
@Tag(name = "Minha conta", description = "O que o usuário logado faz com a própria conta.")
public class MfaController {

    private final GerenciarMfa mfa;
    private final LimiteTentativasLogin limite;

    public MfaController(GerenciarMfa mfa, LimiteTentativasLogin limite) {
        this.mfa = mfa;
        this.limite = limite;
    }

    @GetMapping
    @Operation(summary = "Verificação em duas etapas: situação")
    public MfaSituacaoResponse situacao() {
        return new MfaSituacaoResponse(mfa.ativo(), mfa.disponivel());
    }

    @PostMapping("/configuracao")
    @Operation(summary = "Verificação em duas etapas: configurar", description = """
            Gera o segredo do aplicativo autenticador (QR code a partir de `uri`). Ainda não vale no login: confirme em
            `POST /api/conta/mfa/ativacao`. Chamar de novo descarta o segredo anterior. Já ativa: 409. Ambiente sem a
            chave do MFA (`disponivel: false` na situação): 422.""")
    @ApiResponse(responseCode = "409", description = "Já ativa")
    public MfaConfiguracaoResponse configurar() {
        return MfaConfiguracaoResponse.de(mfa.configurar());
    }

    @PostMapping("/ativacao")
    @Operation(summary = "Verificação em duas etapas: ativar", description = """
            Confere um código do aplicativo e passa a exigi-lo no login. Devolve os códigos de recuperação (guarde-os:
            não aparecem de novo). Encerra as outras sessões do usuário. Código errado conta no limite de tentativas
            do login.""")
    @ApiResponse(responseCode = "422", description = "Código inválido ou não configurada")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    public CodigosRecuperacaoResponse ativar(@Valid @RequestBody CodigoMfaRequest corpo,
                                             @AuthenticationPrincipal UsuarioAutenticado usuario,
                                             HttpServletRequest request) {
        return new CodigosRecuperacaoResponse(limitado(usuario, request, () -> mfa.ativar(corpo.codigo())));
    }

    @PostMapping("/codigos-recuperacao")
    @Operation(summary = "Verificação em duas etapas: novos códigos de recuperação", description = """
            Exige um código do aplicativo. Os códigos anteriores deixam de valer.""")
    @ApiResponse(responseCode = "422", description = "Código inválido ou não ativa")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    public CodigosRecuperacaoResponse gerarCodigos(@Valid @RequestBody CodigoMfaRequest corpo,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                   HttpServletRequest request) {
        return new CodigosRecuperacaoResponse(
                limitado(usuario, request, () -> mfa.gerarCodigosRecuperacao(corpo.codigo())));
    }

    @PostMapping("/desativacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verificação em duas etapas: desativar", description = """
            Exige a senha e um código (do aplicativo ou de recuperação).""")
    @ApiResponse(responseCode = "204", description = "Desativada")
    @ApiResponse(responseCode = "422", description = "Senha ou código incorreto, ou não ativa")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    public void desativar(@Valid @RequestBody DesativarMfaRequest corpo,
                          @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        limitado(usuario, request, () -> {
            mfa.desativar(corpo.senha(), corpo.codigo());
            return List.of();
        });
    }

    /** Mesmo limite da troca de senha: erro conta tentativa; acerto libera. */
    private List<String> limitado(UsuarioAutenticado usuario, HttpServletRequest request, Supplier<List<String>> acao) {
        String ip = request.getRemoteAddr();
        if (!limite.tentar(ip, usuario.email())) {
            throw new TentativasExcedidasException(limite.segundosParaNovaTentativa());
        }
        List<String> resultado = acao.get();
        limite.liberar(ip, usuario.email());
        return resultado;
    }
}
