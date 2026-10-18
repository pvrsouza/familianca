package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarFamilia.FamiliaDetalhe;
import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public record FamiliaResponse(Long id, String nome,
                              @Schema(description = "% da sobra do mês (renda − despesas) que a família quer investir.")
                              BigDecimal metaAportePercentual,
                              @Schema(description = "Regra padrão para estimar os próximos meses das despesas que não se repetem.")
                              Estimativa estimativa,
                              AssinaturaDto assinatura,
                              @Schema(description = "O que vale hoje: o plano da assinatura, ou o gratuito se venceu/cancelou.")
                              PlanoResponse planoEmVigor,
                              List<MembroResponse> membros, List<ConviteResponse> convitesPendentes,
                              @Schema(description = "Se já há saldo informado. Sem ele, lançamentos e recorrências respondem 422.")
                              boolean temSaldoInicial) {

    public static FamiliaResponse de(FamiliaDetalhe d) {
        return new FamiliaResponse(d.familia().id(), d.familia().nome(), Valores.valor(d.familia().metaAporte()),
                d.familia().estimativa(),
                AssinaturaDto.de(d.familia().assinatura()), PlanoResponse.de(d.planoEmVigor()),
                d.membros().stream().map(MembroResponse::de).toList(),
                d.convitesPendentes().stream().map(ConviteResponse::de).toList(), d.temSaldoInicial());
    }
}
