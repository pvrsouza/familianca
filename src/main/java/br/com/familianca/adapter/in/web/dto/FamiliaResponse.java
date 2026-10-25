package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarFamilia.FamiliaDetalhe;
import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record FamiliaResponse(Long id, String nome,
                              @Schema(description = "Meta de partida: % da sobra do mês (renda − despesas) a "
                                      + "investir nos meses em que a regra do aporte não estima.")
                              BigDecimal metaAportePercentual,
                              @Schema(description = "Regra padrão para estimar os próximos meses das despesas que não se repetem.")
                              Estimativa estimativa,
                              @Schema(description = "Regra para estimar a meta de aporte dos meses sem meta definida.")
                              Estimativa estimativaAporte,
                              AssinaturaDto assinatura,
                              @Schema(description = "O que vale hoje: o plano da assinatura, ou o gratuito se venceu/cancelou.")
                              PlanoResponse planoEmVigor,
                              List<MembroResponse> membros, List<ConviteResponse> convitesPendentes,
                              @Schema(description = "Se já há saldo informado. Sem ele, lançamentos e recorrências respondem 422.")
                              boolean temSaldoInicial,
                              @Schema(description = "Ano mais antigo com algum dado (lançamento, recorrência, saldo, "
                                      + "investimento); nenhum = o atual. As telas não voltam antes dele.")
                              int primeiroAno) {

    public static FamiliaResponse de(FamiliaDetalhe d) {
        return new FamiliaResponse(d.familia().id(), d.familia().nome(), Valores.valor(d.familia().metaAporte()),
                d.familia().estimativa(), d.familia().estimativaAporte(),
                AssinaturaDto.de(d.familia().assinatura()), PlanoResponse.de(d.planoEmVigor()),
                d.membros().stream().map(MembroResponse::de).toList(),
                d.convitesPendentes().stream().map(ConviteResponse::de).toList(), d.temSaldoInicial(),
                d.primeiroAno());
    }
}
