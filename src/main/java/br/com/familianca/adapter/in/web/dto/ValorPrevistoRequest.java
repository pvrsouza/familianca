package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ValorPrevistoRequest(
        @Schema(description = "Novo valor previsto desta ocorrência. Nulo = volta ao previsto de sempre.")
        @Positive BigDecimal valor,
        @Schema(description = "Verdadeiro: muda o valor cadastrado deste mês em diante; os meses antes ainda "
                + "previstos ficam como estavam. Falso ou nulo: só este mês.") Boolean daquiEmDiante) {
}
