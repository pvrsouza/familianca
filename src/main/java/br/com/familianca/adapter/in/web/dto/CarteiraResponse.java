package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.CarteiraAnual.Posicao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** Séries de 12 meses (janeiro a dezembro). */
public record CarteiraResponse(int ano,
                               @Schema(description = "Saldo no fim de dezembro do ano anterior.") BigDecimal saldoInicial,
                               List<BigDecimal> aportes, BigDecimal totalAportes,
                               List<BigDecimal> rendimentos, BigDecimal totalRendimentos,
                               @Schema(description = "Saldo no fim de cada mês.") List<BigDecimal> saldos,
                               @Schema(description = "% do mês: rendimento ÷ saldo do mês anterior (nulo sem saldo).")
                               List<BigDecimal> rentabilidadeMensal,
                               @Schema(description = "% composta no ano.") BigDecimal rentabilidadeAno,
                               List<PosicaoResponse> investimentos) {

    public static CarteiraResponse de(CarteiraAnual c) {
        return new CarteiraResponse(c.ano(), c.saldoInicial().valor(), Valores.meses(c.aportes()),
                c.aportes().total().valor(), Valores.meses(c.rendimentos()), c.rendimentos().total().valor(),
                Valores.meses(c.saldos()), Valores.percentuais(c.rentabilidadeMensal()),
                Valores.valor(c.rentabilidadeAno()), c.investimentos().stream().map(PosicaoResponse::de).toList());
    }

    public record PosicaoResponse(InvestimentoResponse investimento, BigDecimal saldoInicial, List<BigDecimal> aportes,
                                  List<BigDecimal> rendimentos, List<BigDecimal> saldos,
                                  List<BigDecimal> rentabilidadeMensal, BigDecimal rentabilidadeAno,
                                  @Schema(description = "Saldo que já existia no primeiro saldo informado (menos o "
                                          + "aporte do mês): não conta como rendimento nem aporte. Nulo = começou do zero.")
                                  AberturaResponse abertura) {

        static PosicaoResponse de(Posicao p) {
            return new PosicaoResponse(InvestimentoResponse.de(p.investimento()), p.saldoInicial().valor(),
                    Valores.meses(p.aportes()), Valores.meses(p.rendimentos()), Valores.meses(p.saldos()),
                    Valores.percentuais(p.rentabilidadeMensal()), Valores.valor(p.rentabilidadeAno()),
                    p.abertura() != null
                            ? new AberturaResponse(p.abertura().competencia().toString(), p.abertura().valor().valor())
                            : null);
        }
    }

    public record AberturaResponse(String competencia, BigDecimal valor) {
    }
}
