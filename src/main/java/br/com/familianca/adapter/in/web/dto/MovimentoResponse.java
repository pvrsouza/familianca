package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.MovimentoInvestimento;

import java.math.BigDecimal;

public record MovimentoResponse(Long investimentoId, String competencia, BigDecimal aporte, BigDecimal rentabilidade,
                                BigDecimal saldoInformado) {

    public static MovimentoResponse de(MovimentoInvestimento m) {
        return new MovimentoResponse(m.investimentoId(), m.competencia().toString(), m.aporte().valor(),
                Valores.valor(m.rentabilidade()), Valores.valor(m.saldoInformado()));
    }
}
