package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarCategorias.DadosCategoria;
import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CategoriaRequest(
        @NotNull Long grupoId,
        @NotBlank @Size(max = 120) String nome,
        @Schema(description = "Nulo = depois das existentes (na criação) ou mantém (na edição).") Integer ordem,
        @Schema(description = "Receita na base das alíquotas da PJ (recurso DESPESAS_PJ).") Boolean tributavel,
        @Schema(description = "Despesa calculada: % sobre as receitas tributáveis do mês (recurso DESPESAS_PJ).",
                example = "6") @DecimalMin("0") @DecimalMax("100") BigDecimal aliquota,
        @Schema(description = "Só na edição. Nulo = ativa.") Boolean ativa,
        @Schema(description = "Costuma se repetir todo mês: ao lançar, o app sugere repetir no ano. "
                + "Nulo = não (na criação) ou mantém (na edição).") Boolean recorrente,
        @Schema(description = "Como estimar os próximos meses da despesa: FAMILIA (segue a regra da família), "
                + "NENHUMA, MEDIA_3M, MEDIA, MAXIMO ou MINIMO. Nulo = segue a família (na criação) ou mantém (na edição).")
        EscolhaEstimativa estimativa) {

    /** A escolha da categoria: a regra da família ou uma regra própria. */
    public enum EscolhaEstimativa { FAMILIA, NENHUMA, MEDIA_3M, MEDIA, MAXIMO, MINIMO }

    public DadosCategoria paraDados() {
        return new DadosCategoria(grupoId, nome, ordem, Boolean.TRUE.equals(tributavel), Valores.percentual(aliquota),
                recorrente, estimativa != null,
                estimativa == null || estimativa == EscolhaEstimativa.FAMILIA ? null : Estimativa.valueOf(estimativa.name()));
    }

    public boolean ativaOuPadrao() {
        return ativa == null || ativa;
    }
}
