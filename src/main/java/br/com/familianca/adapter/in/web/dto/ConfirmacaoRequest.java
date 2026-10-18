package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Corpo opcional: sem ele, vale o valor e o dia da recorrência. */
public record ConfirmacaoRequest(
        @Schema(description = "Valor real. Nulo = o previsto.") @Positive BigDecimal valor,
        @Schema(description = "Data real. Nula = o dia da recorrência na competência.") LocalDate data) {
}
