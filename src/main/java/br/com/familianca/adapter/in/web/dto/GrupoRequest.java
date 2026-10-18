package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarCategorias.DadosGrupo;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GrupoRequest(
        @Schema(description = "Obrigatório na criação; não muda depois.") TipoMovimento tipo,
        @NotNull Natureza natureza,
        @NotBlank @Size(max = 80) String nome,
        @Schema(description = "Grupo de despesas da empresa (recurso DESPESAS_PJ).") Boolean empresa,
        @Schema(description = "Nulo = depois dos existentes (na criação) ou mantém (na edição).") Integer ordem,
        @Schema(description = "Só na edição. Nulo = ativo.") Boolean ativo) {

    public DadosGrupo paraDados() {
        return new DadosGrupo(tipo, natureza, nome, Boolean.TRUE.equals(empresa), ordem);
    }

    public boolean ativoOuPadrao() {
        return ativo == null || ativo;
    }
}
