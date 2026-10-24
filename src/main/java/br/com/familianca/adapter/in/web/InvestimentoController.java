package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.CarteiraResponse;
import br.com.familianca.adapter.in.web.dto.EncerramentoRequest;
import br.com.familianca.adapter.in.web.dto.InvestimentoRequest;
import br.com.familianca.adapter.in.web.dto.InvestimentoResponse;
import br.com.familianca.adapter.in.web.dto.MovimentoRequest;
import br.com.familianca.adapter.in.web.dto.MovimentoResponse;
import br.com.familianca.adapter.in.web.dto.SaldosDoMesResponse;
import br.com.familianca.adapter.in.web.dto.SaldosRequest;
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

    @PostMapping("/{id}/encerramento")
    @Operation(summary = "Encerrar (resgatou tudo)", description = """
            Registra no mês o saldo zero e o resgate do saldo do mês anterior (volta para a conta) e encerra o
            investimento; dali em diante ele fica zerado. Mês que ainda não chegou, antes do primeiro registro ou com
            saldo informado depois dele: 422. Já encerrado: 409. Para voltar a acompanhar, reative (PUT) e informe o
            saldo, que entra como abertura.""")
    public InvestimentoResponse encerrar(@PathVariable Long id, @Valid @RequestBody EncerramentoRequest r) {
        return InvestimentoResponse.de(investimentos.encerrar(id, Competencia.parse(r.mes())));
    }

    @GetMapping("/saldos")
    @Operation(summary = "Saldos do mês", description = """
            Um por investimento ativo que já existia no mês (`mes` AAAA-MM; sem ele, o mês de referência, o anterior
            ao atual): saldo do mês anterior, o informado e se está atualizado (saldo do extrato informado).""")
    public SaldosDoMesResponse saldos(@RequestParam(required = false) String mes) {
        return SaldosDoMesResponse.de(investimentos.saldosDoMes(mes != null ? Competencia.parse(mes) : null));
    }

    @PutMapping("/saldos/{mes}")
    @Operation(summary = "Atualizar os saldos do mês", description = """
            Grava de uma vez o saldo do extrato (e o que colocou ou tirou) de cada investimento enviado; o rendimento
            é o calculado. Os que não vierem ficam como estão. Mês que ainda não chegou: 422.""")
    public SaldosDoMesResponse atualizarSaldos(@PathVariable String mes, @Valid @RequestBody SaldosRequest r) {
        return SaldosDoMesResponse.de(investimentos.atualizarSaldos(Competencia.parse(mes), r.saldos().stream()
                .map(s -> new GerenciarInvestimentos.SaldoInformado(s.investimentoId(), Valores.dinheiro(s.saldo()),
                        Valores.dinheiro(s.aporte())))
                .toList()));
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
