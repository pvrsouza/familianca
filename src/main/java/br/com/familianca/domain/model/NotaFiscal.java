package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;

import java.time.LocalDate;

/**
 * Nota fiscal de uma receita tributável (ex.: notas fiscais emitidas da PJ). O lançamento conta no mês da data do
 * pagamento: é o que entra no caixa e na base das alíquotas. A nota guarda, para conferência e para o contador, de
 * que mês é o serviço (competência) e quando foi emitida. Os dois são opcionais, mas a nota não fica vazia.
 */
public record NotaFiscal(Competencia competencia, LocalDate emissao) {

    public NotaFiscal {
        if (competencia == null && emissao == null) {
            throw new RegraNegocioException("Informem a competência ou a data de emissão da nota");
        }
    }

    /** Nulo quando nem a competência nem a emissão foram informadas. */
    public static NotaFiscal de(Competencia competencia, LocalDate emissao) {
        return competencia == null && emissao == null ? null : new NotaFiscal(competencia, emissao);
    }
}
