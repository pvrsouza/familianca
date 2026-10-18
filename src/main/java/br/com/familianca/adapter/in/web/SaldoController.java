package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.SaldoRequest;
import br.com.familianca.adapter.in.web.dto.SaldoResponse;
import br.com.familianca.adapter.in.web.dto.Valores;
import br.com.familianca.application.port.in.GerenciarLancamentos;
import br.com.familianca.application.port.in.GerenciarSaldos;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saldos")
@Tag(name = "Saldos", description = """
        Saldo em conta no início de cada mês (a "sobra do mês anterior" da planilha), somando as contas da família.
        É o ponto de partida do fluxo de caixa: mês sem saldo informado usa o fluxo líquido calculado do mês
        anterior. Qualquer membro consulta; só o DONO altera.""")
public class SaldoController {

    private final GerenciarSaldos saldos;
    private final GerenciarLancamentos lancamentos;

    public SaldoController(GerenciarSaldos saldos, GerenciarLancamentos lancamentos) {
        this.saldos = saldos;
        this.lancamentos = lancamentos;
    }

    @GetMapping
    @Operation(summary = "Saldos informados no ano", description = "Sem `ano`, o atual.")
    public List<SaldoResponse> listar(@RequestParam(required = false) Integer ano) {
        return saldos.listar(ano != null ? ano : lancamentos.anoAtual()).stream().map(SaldoResponse::de).toList();
    }

    @PutMapping("/{competencia}")
    @Operation(summary = "Informar saldo do mês", description = "Cria ou substitui o saldo da competência (AAAA-MM).")
    public SaldoResponse definir(@PathVariable String competencia, @Valid @RequestBody SaldoRequest r) {
        return SaldoResponse.de(saldos.definir(Competencia.parse(competencia), Valores.dinheiro(r.valor()),
                r.observacao()));
    }

    @DeleteMapping("/{competencia}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover saldo do mês", description = "O mês volta a usar o fluxo do mês anterior.")
    public void remover(@PathVariable String competencia) {
        saldos.remover(Competencia.parse(competencia));
    }
}
