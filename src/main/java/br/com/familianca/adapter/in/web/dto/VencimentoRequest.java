package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record VencimentoRequest(
        @Schema(description = "Novo vencimento desta ocorrência, até 2 meses antes ou depois do mês. Nulo = volta ao "
                + "dia cadastrado na recorrência.") LocalDate vencimento) {
}
