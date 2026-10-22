package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarLancamentos.DadosLancamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoRequest(
        @NotNull Long categoriaId,
        @Schema(description = "De quem é a receita ou despesa. Nulo = quem está lançando. MEMBRO só lança em nome próprio.")
        Long membroId,
        @NotBlank @Size(max = 200) String descricao,
        @NotNull @Positive BigDecimal valor,
        @Schema(description = "Define o mês do lançamento. Na nota fiscal, é o dia em que o dinheiro entrou na conta.") @NotNull LocalDate data,
        @Size(max = 500) String observacao,
        @Schema(description = "Só receita tributável. Nulo = sem nota.") @Valid NotaFiscalDto notaFiscal) {

    public DadosLancamento paraDados() {
        return new DadosLancamento(categoriaId, membroId, descricao, Valores.dinheiro(valor), data, observacao,
                notaFiscal != null ? notaFiscal.paraDominio() : null);
    }
}
