package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.PapelFamilia;
import jakarta.validation.constraints.NotNull;

public record PapelRequest(@NotNull PapelFamilia papel) {
}
