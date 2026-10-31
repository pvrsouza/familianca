package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.GerenciarImportacoes.Acao;
import br.com.familianca.application.port.in.GerenciarImportacoes.LinhaPedido;
import br.com.familianca.application.port.in.GerenciarImportacoes.Pedido;
import br.com.familianca.domain.model.TipoMovimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportacaoRequest(
        @NotNull Long localSaldoId,
        @NotNull LocalDate periodoDe,
        @NotNull LocalDate periodoAte,
        @NotEmpty @Size(max = 5000) List<@Valid @NotNull Linha> linhas) {

    public record Linha(
            @Schema(description = "A chave devolvida pela análise") @NotBlank @Size(max = 255) String chave,
            @NotNull Acao acao,
            @NotNull LocalDate data,
            @Size(max = 1000) String descricao,
            @NotNull @Positive BigDecimal valor,
            @NotNull TipoMovimento tipo,
            @Schema(description = "Nulo = categoria \"A classificar\" do tipo (criada se faltar).") Long categoriaId,
            @Schema(description = "De quem é. Nulo = quem importa. MEMBRO só usa o próprio id.") Long membroId,
            @Schema(description = "Só em CONFIRMAR_RECORRENCIA") Long recorrenciaId,
            @Schema(description = "Só em CONFIRMAR_RECORRENCIA, AAAA-MM") @Size(max = 7) String competencia) {
    }

    public Pedido paraPedido() {
        return new Pedido(localSaldoId, periodoDe, periodoAte, linhas.stream()
                .map(l -> new LinhaPedido(l.chave(), l.acao(), l.data(), l.descricao(), l.valor(), l.tipo(),
                        l.categoriaId(), l.membroId(), l.recorrenciaId(), l.competencia())).toList());
    }
}
