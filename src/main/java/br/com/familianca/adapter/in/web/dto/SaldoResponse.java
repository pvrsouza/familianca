package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.SaldoMes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaldoResponse(String competencia, BigDecimal valor, String observacao, LocalDate dataReferencia,
                            List<PorLocal> porLocal) {

    public record PorLocal(Long localId, BigDecimal valor) {
    }

    public static SaldoResponse de(SaldoMes s) {
        return new SaldoResponse(s.competencia().toString(), s.valor().valor(), s.observacao(), s.dataReferencia(),
                s.porLocal().stream().map(p -> new PorLocal(p.localId(), p.valor().valor())).toList());
    }
}
