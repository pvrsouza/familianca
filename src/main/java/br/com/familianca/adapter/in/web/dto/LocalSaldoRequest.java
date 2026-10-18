package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.TipoLocalSaldo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocalSaldoRequest(
        @NotBlank @Size(max = 80) String nome,
        @NotNull TipoLocalSaldo tipo,
        @Schema(description = "De quem é. Nulo = conjunto ou de alguém sem acesso (ex.: o cofrinho de uma criança).")
        Long membroId,
        @Schema(description = "Só na edição. Nulo = ativo.") Boolean ativo) {

    public boolean ativoOuPadrao() {
        return ativo == null || ativo;
    }
}
