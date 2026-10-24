package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SaldosRequest(@NotNull @Size(min = 1, max = 100) List<@Valid @NotNull Saldo> saldos) {

    public record Saldo(@NotNull Long investimentoId,
                        @Schema(description = "Saldo do extrato no fim do mês.") @NotNull @PositiveOrZero BigDecimal saldo,
                        @Schema(description = "Colocou no mês (negativo = tirou). Nulo = nada.") BigDecimal aporte) {
    }
}
