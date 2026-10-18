package br.com.familianca.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NomeRequest(@NotBlank @Size(max = 200) String nome) {
}
