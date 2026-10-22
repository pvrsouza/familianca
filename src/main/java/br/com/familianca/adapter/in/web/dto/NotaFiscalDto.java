package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.NotaFiscal;
import br.com.familianca.domain.vo.Competencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@Schema(description = """
        Nota fiscal de receita tributável: de que mês é o serviço (competência) e quando a nota foi emitida. O \
        lançamento conta no mês da data do pagamento. Os dois são opcionais; os dois nulos = sem nota.""")
public record NotaFiscalDto(
        @Schema(description = "Mês do serviço (AAAA-MM).", example = "2026-09")
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "competência no formato AAAA-MM") String competencia,
        @Schema(description = "Data de emissão da nota.") LocalDate emissao) {

    public NotaFiscal paraDominio() {
        return NotaFiscal.de(competencia != null ? Competencia.parse(competencia) : null, emissao);
    }

    public static NotaFiscalDto de(NotaFiscal n) {
        return n == null ? null
                : new NotaFiscalDto(n.competencia() != null ? n.competencia().toString() : null, n.emissao());
    }
}
