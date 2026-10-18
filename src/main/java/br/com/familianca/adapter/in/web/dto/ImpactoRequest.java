package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Periodicidade;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ImpactoRequest(
        @Schema(example = "15") @NotNull @Positive BigDecimal valor,
        @Schema(example = "DIAS_UTEIS") @NotNull Periodicidade periodicidade,
        @Schema(description = "Ano usado como base (renda e despesas). Nulo = o atual.") Integer ano) {
}
