package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.adapter.in.web.dto.Referencias.CategoriaRef;
import br.com.familianca.adapter.in.web.dto.Referencias.GrupoRef;
import br.com.familianca.adapter.in.web.dto.Referencias.MembroRef;
import br.com.familianca.application.port.in.GerenciarLancamentos.LancamentoDetalhado;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.TipoMovimento;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record LancamentoResponse(Long id, TipoMovimento tipo, CategoriaRef categoria, GrupoRef grupo,
                                 MembroRef membro, String descricao, BigDecimal valor, LocalDate data,
                                 String competencia,
                                 @Schema(description = "Ocorrência confirmada desta recorrência (nulo = avulso).")
                                 Long recorrenciaId,
                                 String competenciaRecorrencia,
                                 @Schema(description = "Posição na compra parcelada (nulo = à vista).")
                                 ParcelaResponse parcela,
                                 String observacao,
                                 @Schema(description = "Vencimento da conta que este lançamento pagou (confirmação de "
                                         + "despesa que se repete e tem vencimento); nulo = não tem.")
                                 LocalDate vencimento,
                                 @Schema(description = "Dias pagos depois do vencimento: 0 = em dia; nulo = sem vencimento.")
                                 Integer diasAtraso,
                                 @Schema(description = "Competência e emissão da nota (receita tributável); nulo = sem nota.")
                                 NotaFiscalDto notaFiscal) {

    public static LancamentoResponse de(LancamentoDetalhado d) {
        Lancamento l = d.lancamento();
        return new LancamentoResponse(l.id(), d.grupo().tipo(), CategoriaRef.de(d.categoria()), GrupoRef.de(d.grupo()),
                MembroRef.de(d.membro()), l.descricao(), l.valor().valor(), l.data(), l.competencia().toString(),
                l.recorrenciaId(), l.competenciaRecorrencia() != null ? l.competenciaRecorrencia().toString() : null,
                l.parcela() != null ? new ParcelaResponse(l.parcela().parcelamentoId(), l.parcela().numero(),
                        l.parcela().total()) : null,
                l.observacao(), d.vencimento(), diasAtraso(l.data(), d.vencimento()),
                NotaFiscalDto.de(l.notaFiscal()));
    }

    private static Integer diasAtraso(LocalDate pagamento, LocalDate vencimento) {
        if (vencimento == null) {
            return null;
        }
        return pagamento.isAfter(vencimento) ? (int) ChronoUnit.DAYS.between(vencimento, pagamento) : 0;
    }

    public record ParcelaResponse(UUID parcelamentoId, int numero, int total) {
    }
}
