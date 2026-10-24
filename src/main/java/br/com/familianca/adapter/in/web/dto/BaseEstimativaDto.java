package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.BaseEstimativa;
import br.com.familianca.domain.model.Estimativa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Como os próximos meses são previstos: a regra, de quem ela é, desde quando e os meses usados.")
public record BaseEstimativaDto(Estimativa regra,
                                @Schema(description = "DESPESA (regra da própria conta), CATEGORIA ou FAMILIA (padrão).")
                                BaseEstimativa.Origem origem,
                                @Schema(description = "Meses (ou confirmações) que entraram na conta, do mais antigo "
                                        + "ao mais recente.") List<Mes> meses,
                                @Schema(description = "O valor estimado para cada mês que falta; nulo = a regra não "
                                        + "estima (NENHUMA) ou ainda não há base (vale o cadastrado).") BigDecimal valor,
                                @Schema(description = "Primeiro mês em que vale (AAAA-MM): o atual, ou o mês a partir "
                                        + "do qual a regra muda.") String desde) {

    public record Mes(String competencia, BigDecimal valor) {
    }

    public static BaseEstimativaDto de(BaseEstimativa b) {
        return b == null ? null : new BaseEstimativaDto(b.regra(), b.origem(),
                b.meses().stream().map(m -> new Mes(m.competencia().toString(), m.valor().valor())).toList(),
                b.valor() != null ? b.valor().valor() : null, b.desde() != null ? b.desde().toString() : null);
    }

    public static List<BaseEstimativaDto> de(List<BaseEstimativa> trechos) {
        return trechos.stream().map(BaseEstimativaDto::de).toList();
    }
}
