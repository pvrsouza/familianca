package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodigoMfaRequest(
        @Schema(description = "Código de 6 dígitos do aplicativo autenticador.", example = "123456")
        @NotBlank @Size(max = 20) String codigo) {

    @Override
    public String toString() {
        return "CodigoMfaRequest[]";
    }
}
