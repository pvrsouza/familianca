package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.domain.model.Periodicidade;
import br.com.familianca.domain.service.CalculadoraImpacto.Impacto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record ImpactoResponse(BigDecimal valor, Periodicidade periodicidade, int ano, BigDecimal valorAnual,
                              BigDecimal valorEm5Anos, BigDecimal valorEm10Anos,
                              @Schema(description = "% da renda da família no ano (nulo sem renda).") BigDecimal sobreRendaAnual,
                              @Schema(description = "% das despesas da família no ano (nulo sem despesas).")
                              BigDecimal sobreDespesasAnuais,
                              @Schema(description = "Quantos dias de renda da família pagam o gasto do ano.") BigDecimal diasDeRenda,
                              @Schema(description = "Frase pronta para mostrar ao usuário.") String mensagem) {

    public static ImpactoResponse de(Impacto i, int ano) {
        StringBuilder msg = new StringBuilder(i.valor().formatado()).append(' ').append(rotulo(i.periodicidade()));
        if (i.periodicidade() != Periodicidade.UNICA) {
            msg.append(" = ").append(i.valorAnual().formatado()).append(" no ano");
        }
        if (i.sobreRendaAnual() != null) {
            msg.append(" (").append(i.sobreRendaAnual().formatado()).append(" da renda da família");
            if (i.diasDeRenda() != null) {
                msg.append(", ").append(i.diasDeRenda().toPlainString().replace('.', ',')).append(" dias de renda");
            }
            msg.append(')');
        }
        if (i.periodicidade() != Periodicidade.UNICA) {
            msg.append(". Em 10 anos: ").append(i.valorEm10Anos().formatado());
        }
        return new ImpactoResponse(i.valor().valor(), i.periodicidade(), ano, i.valorAnual().valor(),
                i.valorEm5Anos().valor(), i.valorEm10Anos().valor(), Valores.valor(i.sobreRendaAnual()),
                Valores.valor(i.sobreDespesasAnuais()), i.diasDeRenda(), msg.append('.').toString());
    }

    private static String rotulo(Periodicidade p) {
        return switch (p) {
            case UNICA -> "uma vez";
            case DIARIA -> "por dia";
            case DIAS_UTEIS -> "por dia útil";
            case SEMANAL -> "por semana";
            case QUINZENAL -> "por quinzena";
            case MENSAL -> "por mês";
            case BIMESTRAL -> "por bimestre";
            case TRIMESTRAL -> "por trimestre";
            case SEMESTRAL -> "por semestre";
            case ANUAL -> "por ano";
        };
    }
}
