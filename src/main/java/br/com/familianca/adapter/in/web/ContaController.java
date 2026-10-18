package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.seguranca.LimiteTentativasLogin;
import br.com.familianca.adapter.in.seguranca.UsuarioAutenticado;
import br.com.familianca.adapter.in.web.dto.NomeRequest;
import br.com.familianca.adapter.in.web.dto.TrocaSenhaRequest;
import br.com.familianca.application.port.in.GerenciarMinhaConta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conta")
@Tag(name = "Minha conta", description = "O que o usuário logado faz com a própria conta.")
public class ContaController {

    private final GerenciarMinhaConta conta;
    private final LimiteTentativasLogin limite;

    public ContaController(GerenciarMinhaConta conta, LimiteTentativasLogin limite) {
        this.conta = conta;
        this.limite = limite;
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Trocar a própria senha", description = """
            Confere a senha atual e grava a nova (12 a 64 caracteres, diferente da atual). As outras sessões do
            usuário (outros navegadores) são encerradas; esta continua. Senha atual errada conta no mesmo limite de
            tentativas do login (5 em 15 minutos, depois 429).""")
    @ApiResponse(responseCode = "204", description = "Senha trocada")
    @ApiResponse(responseCode = "422", description = "Senha atual incorreta ou nova senha igual à atual")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    public void trocarSenha(@Valid @RequestBody TrocaSenhaRequest corpo,
                            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!limite.tentar(ip, usuario.email())) {
            throw new TentativasExcedidasException(limite.segundosParaNovaTentativa());
        }
        // Senha atual errada lança exceção antes de liberar: a tentativa fica contada.
        conta.trocarSenha(corpo.senhaAtual(), corpo.novaSenha());
        limite.liberar(ip, usuario.email());
    }

    @PutMapping("/nome")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Trocar o próprio nome", description = "Aparece para a família na hora; em `/api/auth/me`, a partir do próximo login.")
    public void renomear(@Valid @RequestBody NomeRequest corpo) {
        conta.renomear(corpo.nome());
    }
}
