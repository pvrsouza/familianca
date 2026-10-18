package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record EstimativaRequest(
        @Schema(description = "MEDIA, MAXIMO, MINIMO ou NENHUMA.", example = "MEDIA") @NotNull Estimativa estimativa) {
}
