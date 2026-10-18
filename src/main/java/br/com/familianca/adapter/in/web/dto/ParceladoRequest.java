package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarLancamentos.DadosLancamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParceladoRequest(
        @NotNull Long categoriaId,
        Long membroId,
        @NotBlank @Size(max = 200) String descricao,
        @Schema(description = "Valor total da compra; os centavos da divisão vão para a 1ª parcela.")
        @NotNull @Positive BigDecimal valorTotal,
        @NotNull @Min(2) @Max(60) Integer parcelas,
        @Schema(description = "Data da 1ª parcela; as demais, no mesmo dia dos meses seguintes.")
        @NotNull LocalDate primeiraData,
        @Size(max = 500) String observacao) {

    public DadosLancamento paraDados() {
        return new DadosLancamento(categoriaId, membroId, descricao, Valores.dinheiro(valorTotal), primeiraData,
                observacao);
    }
}
