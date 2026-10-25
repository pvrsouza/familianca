package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MetaAporteRequest(
        @Schema(description = "% da sobra do mês (renda − despesas) a investir, de 0 a 100.", example = "30")
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentual) {
}
