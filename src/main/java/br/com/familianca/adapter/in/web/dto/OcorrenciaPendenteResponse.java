package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarRecorrencias.OcorrenciaPendente;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OcorrenciaPendenteResponse(String competencia, LocalDate data, BigDecimal valorPrevisto,
                                         RecorrenciaResponse recorrencia,
                                         @Schema(description = "Vencimento desta ocorrência; nulo = sem vencimento.")
                                         LocalDate vencimento) {

    public static OcorrenciaPendenteResponse de(OcorrenciaPendente p) {
        return new OcorrenciaPendenteResponse(p.competencia().toString(), p.data(),
                p.valorPrevisto().valor(), RecorrenciaResponse.de(p.recorrencia()), p.vencimento());
    }
}
