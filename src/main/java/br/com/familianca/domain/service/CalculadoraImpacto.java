package br.com.familianca.domain.service;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Periodicidade;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * O diferencial do produto: quanto um gasto pequeno representa no ano. Ex.: R$ 15 por dia útil = R$ 3.780 no ano; com renda de
 * R$ 90 mil por ano, são 4,2% da renda e 15,3 dias de renda da família.
 */
public final class CalculadoraImpacto {

    /**
     * @param diasDeRenda quantos dias da renda anual da família pagam o gasto do ano (nulo sem renda)
     */
    public record Impacto(Dinheiro valor, Periodicidade periodicidade, Dinheiro valorAnual, Dinheiro valorEm5Anos,
                          Dinheiro valorEm10Anos, Percentual sobreRendaAnual, Percentual sobreDespesasAnuais,
                          BigDecimal diasDeRenda) {
    }

    public Impacto calcular(Dinheiro valor, Periodicidade periodicidade, Dinheiro rendaAnual, Dinheiro despesasAnuais) {
        if (valor == null || !valor.ehPositivo()) {
            throw new RegraNegocioException("O valor deve ser maior que zero");
        }
        if (periodicidade == null) {
            throw new RegraNegocioException("Campo obrigatório: periodicidade");
        }
        Dinheiro anual = valor.multiplicar(periodicidade.vezesPorAno());
        boolean unica = periodicidade == Periodicidade.UNICA;
        BigDecimal dias = rendaAnual.ehPositivo()
                ? anual.valor().multiply(BigDecimal.valueOf(365)).divide(rendaAnual.valor(), 1, RoundingMode.HALF_UP)
                : null;
        return new Impacto(valor, periodicidade, anual, unica ? anual : anual.multiplicar(5),
                unica ? anual : anual.multiplicar(10), Percentual.razao(anual, rendaAnual),
                Percentual.razao(anual, despesasAnuais), dias);
    }
}
