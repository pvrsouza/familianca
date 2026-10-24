package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarInvestimentos.SaldosDoMes;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record SaldosDoMesResponse(String competencia,
                                  @Schema(description = "Mês de referência (o anterior ao atual): o que os lembretes cobram.")
                                  String referencia,
                                  @Schema(description = "Investimentos ativos que já existiam no mês.") int total,
                                  @Schema(description = "Quantos têm o saldo do extrato informado no mês.") long atualizados,
                                  List<Linha> linhas) {

    public record Linha(InvestimentoResponse investimento,
                        @Schema(description = "Saldo no fim do mês anterior.") BigDecimal saldoAnterior,
                        @Schema(description = "Colocou (ou tirou, negativo) no mês; nulo = nada registrado.")
                        BigDecimal aporte,
                        @Schema(description = "Saldo do extrato informado no mês; nulo = ainda não atualizado.")
                        BigDecimal saldoInformado,
                        boolean atualizado) {
    }

    public static SaldosDoMesResponse de(SaldosDoMes s) {
        return new SaldosDoMesResponse(s.competencia().toString(), s.referencia().toString(), s.linhas().size(),
                s.atualizados(), s.linhas().stream().map(l -> new Linha(InvestimentoResponse.de(l.investimento()),
                        l.saldoAnterior().valor(), l.aporte() != null ? l.aporte().valor() : null,
                        l.saldoInformado() != null ? l.saldoInformado().valor() : null, l.atualizado())).toList());
    }
}
