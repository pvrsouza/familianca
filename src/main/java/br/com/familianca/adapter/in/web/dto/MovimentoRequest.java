package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record MovimentoRequest(
        @Schema(description = "Aplicado no mês (negativo = resgate). Nulo = 0.") BigDecimal aporte,
        @Schema(description = "% do mês sobre o saldo do mês anterior.", example = "0.95") BigDecimal rentabilidade,
        @Schema(description = "Saldo do extrato no fim do mês; prevalece sobre o calculado.") @PositiveOrZero
        BigDecimal saldoInformado) {
}
