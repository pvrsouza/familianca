package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.PapelFamilia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConviteRequest(
        @NotBlank @Email @Size(max = 200) String email,
        @Schema(description = "Padrão MEMBRO.") PapelFamilia papel) {
}
