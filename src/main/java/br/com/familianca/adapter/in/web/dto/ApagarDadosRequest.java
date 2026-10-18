package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApagarDadosRequest(
        @Schema(description = "Senha de quem pede, conferida de novo por ser uma ação sem volta.")
        @NotBlank @Size(max = 200) String senha) {

    @Override
    public String toString() {
        return "ApagarDadosRequest[]";
    }
}
