package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record MfaSituacaoResponse(
        boolean ativo,
        @Schema(description = "false: o ambiente está sem a chave do MFA; não oferecer a opção.") boolean disponivel) {
}
