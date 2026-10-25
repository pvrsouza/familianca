package br.com.familianca.domain.model;

import br.com.familianca.domain.model.VisaoAnual.BaseMeta;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Percentual;

import java.util.List;
import java.util.Map;

/**
 * Como a meta de aporte de cada mês é definida, além da meta de partida da família.
 *
 * @param regra      como estimar os meses sem meta definida, a partir dos meses anteriores
 * @param doMes      metas definidas à mão por mês
 * @param anteriores a base do ano anterior (a média dos últimos 3 meses cruza o ano)
 * @param conheceAportes false sem o recurso de investimentos: não há aporte registrado, então mês fechado entra na
 *                   base com a sua meta (o realizado seria sempre 0%)
 */
public record MetasAporte(Estimativa regra, Map<Competencia, Percentual> doMes, List<BaseMeta> anteriores,
                          boolean conheceAportes) {

    /** Só a meta de partida, em todo mês. */
    public static final MetasAporte NENHUMA = new MetasAporte(Estimativa.NENHUMA, Map.of(), List.of(), true);

    public MetasAporte {
        doMes = Map.copyOf(doMes);
        anteriores = List.copyOf(anteriores);
    }
}
