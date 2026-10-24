package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Variação mês a mês de uma série ("inflação" do grupo ou da família) e a acumulada no ano, composta como na
 * planilha: (1 + v₂) × (1 + v₃) × … × (1 + v₁₂) − 1. Diferença deliberada: mês com valor zero (dele ou do anterior)
 * não tem variação (nulo) e fica fora do acumulado, em vez de entrar como −100%.
 *
 * @param mensal 12 posições; janeiro é sempre nulo (não há mês anterior no ano)
 */
public record Variacao(List<Percentual> mensal, Percentual acumulada) {

    public Variacao {
        mensal = Collections.unmodifiableList(new ArrayList<>(mensal));
    }

    public static Variacao de(SerieMensal serie) {
        return de(serie, SerieMensal.MESES);
    }

    /**
     * Só até o último mês fechado: o mês em andamento (gasto incompleto) e os previstos não têm variação nem entram no
     * acumulado, que fica sendo "até" esse mês. 0 = nenhum mês fechado (ano futuro, ou janeiro do ano corrente).
     */
    public static Variacao de(SerieMensal serie, int ultimoMesFechado) {
        List<Percentual> mensal = new ArrayList<>(SerieMensal.MESES);
        mensal.add(null);
        BigDecimal fator = BigDecimal.ONE;
        boolean algum = false;
        for (int mes = 2; mes <= SerieMensal.MESES; mes++) {
            if (mes > ultimoMesFechado) {
                mensal.add(null);
                continue;
            }
            Dinheiro anterior = serie.mes(mes - 1);
            Dinheiro atual = serie.mes(mes);
            Percentual v = Percentual.variacao(atual, anterior);
            mensal.add(v);
            if (v != null) {
                fator = fator.multiply(atual.valor().divide(anterior.valor(), MathContext.DECIMAL64));
                algum = true;
            }
        }
        return new Variacao(mensal, algum ? Percentual.deFracao(fator.subtract(BigDecimal.ONE)) : null);
    }
}
