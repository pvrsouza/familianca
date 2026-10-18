package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.TipoInvestimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvestimentoRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotNull TipoInvestimento tipo,
        @Schema(description = "Só na edição. Nulo = ativo.") Boolean ativo) {

    public boolean ativoOuPadrao() {
        return ativo == null || ativo;
    }
}
