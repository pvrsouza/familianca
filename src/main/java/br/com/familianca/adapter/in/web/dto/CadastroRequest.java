package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroRequest(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(min = Usuario.SENHA_MINIMO, max = Usuario.SENHA_MAXIMO) String senha,
        @Schema(description = "Opcional. Em branco = \"Família de <primeiro nome>\".") @Size(max = 100) String nomeFamilia) {

    @Override
    public String toString() {
        return "CadastroRequest[]";
    }
}
