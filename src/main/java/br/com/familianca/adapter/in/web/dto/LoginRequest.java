package br.com.familianca.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tamanhos máximos só para não processar entradas absurdas; a política de senha vale no cadastro. */
public record LoginRequest(
        @NotBlank @Size(max = 200) String email,
        @NotBlank @Size(max = 200) String senha,
        @Schema(description = """
                Só para quem ativou a verificação em duas etapas: código de 6 dígitos do aplicativo autenticador ou um
                código de recuperação. Sem ele, o login responde 401 com `mfaObrigatorio: true`.""")
        @Size(max = 20) String codigoMfa) {

    @Override
    public String toString() {
        return "LoginRequest[]";
    }
}
