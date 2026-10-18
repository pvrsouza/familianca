package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarRecorrencias.DadosRecorrencia;
import br.com.familianca.domain.model.FormaPagamento;
import br.com.familianca.domain.model.Frequencia;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RecorrenciaRequest(
        @NotNull Long categoriaId,
        @Schema(description = "Nulo = quem está cadastrando. MEMBRO só cadastra em nome próprio.") Long membroId,
        @NotBlank @Size(max = 200) String descricao,
        @Schema(description = "Valor previsto de cada ocorrência.") @NotNull @Positive BigDecimal valor,
        @Schema(description = """
                Despesa: dia do vencimento. Receita: dia em que costuma cair. 31 em meses curtos vira o último dia.""")
        @NotNull @Min(1) @Max(31) Integer dia,
        @Schema(description = "UNICA = uma vez só, no mês de início (conta avulsa; o fim é ignorado).")
        @NotNull Frequencia frequencia,
        @Schema(description = "Primeira competência (AAAA-MM).", example = "2026-01") @NotBlank String inicio,
        @Schema(description = "Última competência (AAAA-MM). Nulo = sem prazo.", example = "2026-12") String fim,
        @Size(max = 500) String observacao,
        @Schema(description = """
                Só despesa: cada ocorrência vira lançamento sozinha no vencimento, com o valor previsto (débito \
                automático, conta de valor fixo). Vale para o que vence a partir do dia em que foi ligada. Nulo = \
                desligada.""") Boolean confirmarSozinha,
        @Schema(description = """
                Só despesa: a conta não tem data certa para pagar (ex.: mesada). O dia só posiciona a ocorrência no                 mês e não há "em dia / com atraso". Nulo = tem vencimento.""") Boolean semVencimento,
        @Schema(description = """
                Só despesa: BOLETO_PIX (alguém paga até o vencimento), DEBITO_AUTOMATICO (sai sozinho; basta                 conferir) ou CARTAO_CREDITO (vai para a fatura; sem vencimento próprio). Nulo = não informado.""")
        FormaPagamento formaPagamento) {

    public DadosRecorrencia paraDados() {
        return new DadosRecorrencia(categoriaId, membroId, descricao, Valores.dinheiro(valor), dia, frequencia,
                Competencia.parse(inicio), fim != null && !fim.isBlank() ? Competencia.parse(fim) : null, observacao,
                Boolean.TRUE.equals(confirmarSozinha), Boolean.TRUE.equals(semVencimento), formaPagamento);
    }
}
