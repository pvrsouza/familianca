package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;

public record EstimativaPropriaRequest(
        @Schema(description = "MEDIA_3M, MEDIA, MAXIMO, MINIMO ou NENHUMA. Nulo = volta a seguir a regra de cima "
                + "(a conta segue a categoria; a categoria segue a família).", example = "MEDIA_3M")
        Estimativa estimativa) {
}
