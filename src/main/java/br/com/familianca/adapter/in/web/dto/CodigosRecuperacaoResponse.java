package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CodigosRecuperacaoResponse(
        @Schema(description = "Cada um vale uma vez no lugar do código do aplicativo. Mostrados só nesta resposta.")
        List<String> codigosRecuperacao) {
}
