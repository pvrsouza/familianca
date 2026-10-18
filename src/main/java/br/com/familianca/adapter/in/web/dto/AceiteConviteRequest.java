package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O e-mail da conta é o do convite. */
public record AceiteConviteRequest(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Size(min = Usuario.SENHA_MINIMO, max = Usuario.SENHA_MAXIMO) String senha) {

    @Override
    public String toString() {
        return "AceiteConviteRequest[]";
    }
}
