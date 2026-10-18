package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.SaldoMes;

import java.math.BigDecimal;

public record SaldoResponse(String competencia, BigDecimal valor, String observacao) {

    public static SaldoResponse de(SaldoMes s) {
        return new SaldoResponse(s.competencia().toString(), s.valor().valor(), s.observacao());
    }
}
