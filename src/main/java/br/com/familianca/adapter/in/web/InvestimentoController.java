package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.CarteiraResponse;
import br.com.familianca.adapter.in.web.dto.InvestimentoRequest;
import br.com.familianca.adapter.in.web.dto.InvestimentoResponse;
import br.com.familianca.adapter.in.web.dto.MovimentoRequest;
import br.com.familianca.adapter.in.web.dto.MovimentoResponse;
import br.com.familianca.adapter.in.web.dto.Valores;
import br.com.familianca.application.port.in.GerenciarInvestimentos;
import br.com.familianca.application.port.in.GerenciarLancamentos;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/investimentos")
@Tag(name = "Investimentos", description = """
        Carteira da família (recurso INVESTIMENTOS do plano; sem ele, 402). Por mês e investimento: aporte (negativo
        = resgate), rentabilidade (%) e, se quiser, o saldo do extrato, que prevalece sobre o calculado. Saldo do mês
        = saldo anterior + rendimento + aporte. Os aportes entram no fluxo de caixa como aplicações, e os rendimentos
        dão o grau de independência financeira. Qualquer membro consulta; só o DONO altera.""")
public class InvestimentoController {

    private final GerenciarInvestimentos investimentos;
    private final GerenciarLancamentos lancamentos;

    public InvestimentoController(GerenciarInvestimentos investimentos, GerenciarLancamentos lancamentos) {
        this.investimentos = investimentos;
        this.lancamentos = lancamentos;
    }

    @GetMapping
    @Operation(summary = "Listar investimentos")
    @ApiResponse(responseCode = "402", description = "Sem o recurso INVESTIMENTOS")
    public List<InvestimentoResponse> listar() {
        return investimentos.listar().stream().map(InvestimentoResponse::de).toList();
    }

    @GetMapping("/carteira")
    @Operation(summary = "Carteira no ano", description = "Séries mensais por investimento e da carteira. Sem `ano`, o atual.")
    public CarteiraResponse carteira(@RequestParam(required = false) Integer ano) {
        return CarteiraResponse.de(investimentos.carteira(ano != null ? ano : lancamentos.anoAtual()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar investimento")
    @ApiResponse(responseCode = "409", description = "Nome repetido")
    public InvestimentoResponse criar(@Valid @RequestBody InvestimentoRequest r) {
        return InvestimentoResponse.de(investimentos.criar(r.nome(), r.tipo()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar, desativar ou reativar investimento")
    public InvestimentoResponse atualizar(@PathVariable Long id, @Valid @RequestBody InvestimentoRequest r) {
        return InvestimentoResponse.de(investimentos.atualizar(id, r.nome(), r.tipo(), r.ativoOuPadrao()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir investimento sem movimentos")
    @ApiResponse(responseCode = "409", description = "Tem movimentos: desative")
    public void remover(@PathVariable Long id) {
        investimentos.remover(id);
    }

    @GetMapping("/{id}/movimentos")
    @Operation(summary = "Movimentos do investimento", description = "Em ordem de competência.")
    public List<MovimentoResponse> movimentos(@PathVariable Long id) {
        return investimentos.listarMovimentos(id).stream().map(MovimentoResponse::de).toList();
    }

    @PutMapping("/{id}/movimentos/{competencia}")
    @Operation(summary = "Registrar movimento do mês", description = "Cria ou substitui o movimento da competência (AAAA-MM).")
    public MovimentoResponse registrarMovimento(@PathVariable Long id, @PathVariable String competencia,
                                                @Valid @RequestBody MovimentoRequest r) {
        return MovimentoResponse.de(investimentos.registrarMovimento(new MovimentoInvestimento(id,
                Competencia.parse(competencia), Valores.dinheiro(r.aporte()), Valores.percentual(r.rentabilidade()),
                Valores.dinheiro(r.saldoInformado()))));
    }

    @DeleteMapping("/{id}/movimentos/{competencia}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover movimento do mês")
    public void removerMovimento(@PathVariable Long id, @PathVariable String competencia) {
        investimentos.removerMovimento(id, Competencia.parse(competencia));
    }
}
