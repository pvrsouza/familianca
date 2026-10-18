package br.com.familianca.adapter.in.web;

import br.com.familianca.adapter.in.web.dto.MovimentoDoMesResponse;
import br.com.familianca.adapter.in.web.dto.SaldoRequest;
import br.com.familianca.adapter.in.web.dto.SaldoResponse;
import br.com.familianca.adapter.in.web.dto.Valores;
import br.com.familianca.application.port.in.GerenciarLancamentos;
import br.com.familianca.application.port.in.GerenciarSaldos;
import br.com.familianca.domain.model.SaldoMes;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/saldos")
@Tag(name = "Saldos", description = """
        Saldo em conta no dia 1º de cada mês (a "sobra do mês anterior" da planilha), somando as contas da família.
        É o ponto de partida do fluxo de caixa: mês sem saldo informado usa o fluxo líquido calculado do mês
        anterior. A família pode informar o saldo de outro dia do mês (ex.: hoje): o fluxo usa como dia 1º esse
        valor sem o que foi lançado de 1º até o dia. O saldo pode vir item a item (conta corrente, poupança, dinheiro
        na mão, cofrinho...): o total é a soma. Na prática a família informa o saldo inicial uma vez e depois só
        corrige quando o banco mostrar outro valor. Qualquer membro consulta; só o DONO altera.""")
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
    @Operation(summary = "Informar saldo do mês", description = """
            Cria ou substitui o saldo da competência (AAAA-MM). Com `dataReferencia`, o valor é o saldo nesse dia.
            Com `porLocal`, o total é a soma do saldo de cada lugar (cadastrados em /api/locais-saldo).""")
    public SaldoResponse definir(@PathVariable String competencia, @Valid @RequestBody SaldoRequest r) {
        List<SaldoMes.PorLocal> porLocal = r.porLocal() == null ? List.of() : r.porLocal().stream()
                .map(p -> new SaldoMes.PorLocal(p.localId(), Valores.dinheiro(p.valor()))).toList();
        return SaldoResponse.de(saldos.definir(Competencia.parse(competencia),
                Valores.dinheiro(r.valor()), porLocal, r.observacao(), r.dataReferencia()));
    }

    @GetMapping("/movimento")
    @Operation(summary = "Lançado no mês até um dia", description = """
            Receitas e despesas lançadas do dia 1º do mês de `ate` até `ate`: a conta do saldo desse dia para o do
            dia 1º, para mostrar antes de guardar. Só o DONO.""")
    public MovimentoDoMesResponse movimento(@RequestParam LocalDate ate) {
        return MovimentoDoMesResponse.de(saldos.movimentoAte(ate));
    }

    @DeleteMapping("/{competencia}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remover saldo do mês", description = "O mês volta a usar o fluxo do mês anterior.")
    public void remover(@PathVariable String competencia) {
        saldos.remover(Competencia.parse(competencia));
    }
}
