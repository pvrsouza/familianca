package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.seguranca.LimiteTentativasLogin;
import br.com.familianca.adapter.in.web.dto.AceiteConviteRequest;
import br.com.familianca.adapter.in.web.dto.CadastroRequest;
import br.com.familianca.adapter.in.web.dto.ContaCriadaResponse;
import br.com.familianca.adapter.in.web.dto.ConviteAbertoResponse;
import br.com.familianca.application.port.in.AceitarConvite;
import br.com.familianca.application.port.in.CadastrarFamilia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas públicas que criam conta. Contam no mesmo limite de tentativas do login, por IP. */
@RestController
@Tag(name = "Cadastro e convites", description = """
        Rotas públicas (sem login, com token CSRF). O cadastro cria a família, com o plano completo em teste e as
        categorias padrão, e quem se cadastra vira DONO. Quem recebe um convite cria a conta pelo link, já dentro da
        família. Depois, entre por `POST /api/auth/login`.""")
public class CadastroController {

    private static final String CHAVE_LIMITE = "cadastro";

    private final CadastrarFamilia cadastro;
    private final AceitarConvite convites;
    private final LimiteTentativasLogin limite;

    public CadastroController(CadastrarFamilia cadastro, AceitarConvite convites, LimiteTentativasLogin limite) {
        this.cadastro = cadastro;
        this.convites = convites;
        this.limite = limite;
    }

    @PostMapping("/api/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar conta e família", description = "Senha de 12 a 64 caracteres. E-mail já cadastrado: 409.")
    @ApiResponse(responseCode = "201", description = "Conta e família criadas")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas a partir deste IP")
    public ContaCriadaResponse cadastrar(@Valid @RequestBody CadastroRequest r, HttpServletRequest request) {
        limitar(request);
        return ContaCriadaResponse.de(cadastro.cadastrar(r.nome(), r.email(), r.senha(), r.nomeFamilia()));
    }

    @GetMapping("/api/convites/{token}")
    @Operation(summary = "Ver convite", description = "Família, e-mail e papel do convite. Usado, cancelado ou expirado: 404.")
    @ApiResponse(responseCode = "404", description = "Convite não encontrado ou expirado")
    public ConviteAbertoResponse consultar(@PathVariable String token) {
        return ConviteAbertoResponse.de(convites.consultar(token));
    }

    @PostMapping("/api/convites/{token}/aceite")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Aceitar convite", description = """
            Cria a conta com o e-mail do convite e entra na família com o papel convidado. O convite vale uma vez.
            E-mail já com conta: 409 (por enquanto cada pessoa participa de uma única família). Família no limite de
            pessoas do plano: 402.""")
    @ApiResponse(responseCode = "201", description = "Conta criada na família")
    @ApiResponse(responseCode = "402", description = "Limite de pessoas do plano")
    @ApiResponse(responseCode = "404", description = "Convite não encontrado ou expirado")
    @ApiResponse(responseCode = "409", description = "E-mail já tem conta")
    public ContaCriadaResponse aceitar(@PathVariable String token, @Valid @RequestBody AceiteConviteRequest r,
                                       HttpServletRequest request) {
        limitar(request);
        return ContaCriadaResponse.de(convites.aceitar(token, r.nome(), r.senha()));
    }

    private void limitar(HttpServletRequest request) {
        if (!limite.tentar(request.getRemoteAddr(), CHAVE_LIMITE)) {
            throw new TentativasExcedidasException(limite.segundosParaNovaTentativa());
        }
    }
}
