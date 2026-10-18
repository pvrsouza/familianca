package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FamiliaRequest(
        @NotBlank @Size(max = 100) String nome,
        @Schema(description = "0 a 100. Nulo = sem meta (sem previsão de aporte).", example = "20")
        @DecimalMin("0") @DecimalMax("100") BigDecimal metaAportePercentual) {
}
