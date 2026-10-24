package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

public record EstimativaPropriaRequest(
        @Schema(description = "MEDIA_3M, MEDIA, MAXIMO, MINIMO ou NENHUMA. Nulo = volta a seguir a regra de cima "
                + "(a conta segue a categoria; a categoria segue a família).", example = "MEDIA_3M")
        Estimativa estimativa,
        @Schema(description = "A partir de qual mês (AAAA-MM, o atual ou depois). Nulo = todos os meses previstos, "
                + "mantendo os valores ajustados à mão. Com mês: os meses antes continuam com a regra de hoje e os "
                + "ajustados à mão desse mês em diante voltam a seguir a regra.", example = "2027-01")
        @Pattern(regexp = "^[0-9]{4}-(0[1-9]|1[0-2])$", message = "use o formato AAAA-MM") String desde) {
}
