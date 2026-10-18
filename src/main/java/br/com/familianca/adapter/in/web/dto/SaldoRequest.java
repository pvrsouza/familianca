package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SaldoRequest(
        @Schema(description = "Saldo somado das contas no início do mês. Pode ser negativo.") @NotNull BigDecimal valor,
        @Schema(description = "Ex.: quais contas entraram na soma.") @Size(max = 200) String observacao) {
}
