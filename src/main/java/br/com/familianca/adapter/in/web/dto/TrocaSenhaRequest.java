package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaRequest(
        @NotBlank @Size(max = 200) String senhaAtual,
        @NotBlank @Size(min = Usuario.SENHA_MINIMO, max = Usuario.SENHA_MAXIMO) String novaSenha) {

    @Override
    public String toString() {
        return "TrocaSenhaRequest[]";
    }
}
