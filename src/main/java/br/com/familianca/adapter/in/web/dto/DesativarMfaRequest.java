package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DesativarMfaRequest(
        @NotBlank @Size(max = 200) String senha,
        @Schema(description = "Código do aplicativo ou de recuperação.") @NotBlank @Size(max = 20) String codigo) {

    @Override
    public String toString() {
        return "DesativarMfaRequest[]";
    }
}
