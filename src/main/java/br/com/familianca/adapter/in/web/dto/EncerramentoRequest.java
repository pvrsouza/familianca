package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EncerramentoRequest(
        @Schema(description = "Mês em que resgatou tudo (AAAA-MM).", example = "2026-10") @NotBlank @Size(max = 7)
        String mes) {
}
