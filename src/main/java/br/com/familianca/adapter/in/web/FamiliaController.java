package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.seguranca.LimiteTentativasLogin;
import br.com.familianca.adapter.in.seguranca.UsuarioAutenticado;
import br.com.familianca.adapter.in.web.dto.ApagarDadosRequest;
import br.com.familianca.adapter.in.web.dto.ConviteRequest;
import br.com.familianca.adapter.in.web.dto.ConviteResponse;
import br.com.familianca.adapter.in.web.dto.EstimativaRequest;
import br.com.familianca.adapter.in.web.dto.FamiliaRequest;
import br.com.familianca.adapter.in.web.dto.FamiliaResponse;
import br.com.familianca.adapter.in.web.dto.MembroResponse;
import br.com.familianca.adapter.in.web.dto.PapelRequest;
import br.com.familianca.adapter.in.web.dto.Valores;
import br.com.familianca.application.port.in.GerenciarFamilia;
import br.com.familianca.application.port.in.GerenciarFamilia.ConviteCriado;
import br.com.familianca.domain.model.PapelFamilia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/familia")
@Tag(name = "Família", description = """
        A família do usuário logado: membros, convites, meta de aporte e o plano em vigor. Qualquer membro consulta;
        só o **DONO** altera (MEMBRO recebe 403). Remover um membro o inativa (os lançamentos dele continuam no
        histórico) e encerra as sessões dele; o mesmo vale para a troca de papel. Ninguém remove nem muda o papel de
        si mesmo, e sempre sobra um DONO ativo.""")
public class FamiliaController {

    private final GerenciarFamilia familia;
    private final LimiteTentativasLogin limite;
    private final String urlConvite;

    public FamiliaController(GerenciarFamilia familia, LimiteTentativasLogin limite,
                             @Value("${familianca.convites.url-base:http://localhost:5174/convite}") String urlConvite) {
        this.familia = familia;
        this.limite = limite;
        this.urlConvite = urlConvite;
    }

    @GetMapping
    @Operation(summary = "Minha família")
    public FamiliaResponse consultar() {
        return FamiliaResponse.de(familia.consultar());
    }

    @PutMapping
    @Operation(summary = "Atualizar nome e meta de aporte")
    public FamiliaResponse atualizar(@Valid @RequestBody FamiliaRequest r) {
        familia.atualizar(r.nome(), Valores.percentual(r.metaAportePercentual()));
        return consultar();
    }

    @PutMapping("/estimativa")
    @Operation(summary = "Regra padrão para estimar os próximos meses",
            description = """
                    Despesas que não se repetem (supermercado, farmácia) ganham nos meses que faltam do ano uma \
                    estimativa a partir dos meses que já passaram: MEDIA_3M (média dos últimos 3 meses, padrão), \
                    MEDIA, MAXIMO, MINIMO (do ano) ou NENHUMA. Cada categoria pode seguir esta regra ou ter a sua \
                    (PUT /api/categorias/{id}/estimativa), e cada conta que se repete pode ter a dela \
                    (PUT /api/recorrencias/{id}/estimativa).""")
    public FamiliaResponse definirEstimativa(@Valid @RequestBody EstimativaRequest r) {
        familia.definirEstimativa(r.estimativa());
        return consultar();
    }

    @PutMapping("/membros/{id}/papel")
    @Operation(summary = "Mudar o papel de um membro")
    @ApiResponse(responseCode = "422", description = "O próprio papel, ou o último DONO")
    public MembroResponse alterarPapel(@PathVariable Long id, @Valid @RequestBody PapelRequest r) {
        return MembroResponse.de(familia.alterarPapel(id, r.papel()));
    }

    @DeleteMapping("/membros/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover membro", description = "Inativa: não entra mais; o histórico fica.")
    public void removerMembro(@PathVariable Long id) {
        familia.removerMembro(id);
    }

    @PostMapping("/convites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convidar alguém", description = """
            Gera um link de convite (válido por 7 dias, uso único) para enviar à pessoa. O token só aparece nesta
            resposta. Conta no limite de pessoas do plano (402). E-mail já na família, já com conta ou com convite
            pendente: 409.""")
    @ApiResponse(responseCode = "201", description = "Convite criado; envie o `link`")
    @ApiResponse(responseCode = "402", description = "Limite de pessoas do plano")
    @ApiResponse(responseCode = "409", description = "E-mail já na família, com conta ou com convite pendente")
    public ConviteResponse convidar(@Valid @RequestBody ConviteRequest r) {
        ConviteCriado criado = familia.convidar(r.email(), r.papel() != null ? r.papel() : PapelFamilia.MEMBRO);
        return ConviteResponse.criado(criado.convite(), criado.token(), urlConvite);
    }

    @DeleteMapping("/convites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancelar convite pendente")
    public void cancelarConvite(@PathVariable Long id) {
        familia.cancelarConvite(id);
    }

    @PostMapping("/dados/apagamento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Apagar todos os dados da família", description = """
            Apaga, sem volta, todo o orçamento da família: lançamentos, recorrências, saldos informados,
            investimentos e o plano de contas (que volta a ser o padrão de família nova). Pessoas, convites e
            assinatura continuam. Só DONO, com a própria senha; senha errada conta no mesmo limite de tentativas do
            login (5 em 15 minutos, depois 429).""")
    @ApiResponse(responseCode = "204", description = "Dados apagados; categorias padrão recriadas")
    @ApiResponse(responseCode = "422", description = "Senha incorreta")
    @ApiResponse(responseCode = "429", description = "Muitas tentativas")
    public void apagarDados(@Valid @RequestBody ApagarDadosRequest corpo,
                            @AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (!limite.tentar(ip, usuario.email())) {
            throw new TentativasExcedidasException(limite.segundosParaNovaTentativa());
        }
        // Senha errada lança exceção antes de liberar: a tentativa fica contada.
        familia.apagarDados(corpo.senha());
        limite.liberar(ip, usuario.email());
    }
}
