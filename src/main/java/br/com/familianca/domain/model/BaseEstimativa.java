package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.util.List;

/**
 * Como os próximos meses são previstos, para a tela mostrar a conta: a regra, de quem é a regra, desde quando ela vale
 * e os meses (ou confirmações) que entraram, com o valor de cada um.
 *
 * @param valor o valor previsto pela regra; nulo = a regra não estima (NENHUMA) ou ainda não há base (vale o
 *              cadastrado, na conta que se repete; nada é estimado, na despesa avulsa)
 * @param desde o primeiro mês em que vale (o mês atual, ou o mês a partir do qual a regra mudou); nulo = sem mês
 */
public record BaseEstimativa(Estimativa regra, Origem origem, List<Mes> meses, Dinheiro valor, Competencia desde) {

    /** De quem é a regra usada: a própria conta que se repete, a categoria ou o padrão da família. */
    public enum Origem { DESPESA, CATEGORIA, FAMILIA }

    public record Mes(Competencia competencia, Dinheiro valor) {
    }

    public BaseEstimativa {
        meses = List.copyOf(meses);
    }

    public BaseEstimativa(Estimativa regra, Origem origem, List<Mes> meses, Dinheiro valor) {
        this(regra, origem, meses, valor, null);
    }

    /** A regra estima um valor (tem base). */
    public boolean estima() {
        return valor != null;
    }

    /** O trecho que vale na competência: o último que começa nela ou antes (nulo se todos começam depois). */
    public static BaseEstimativa em(List<BaseEstimativa> trechos, Competencia competencia) {
        BaseEstimativa vale = null;
        for (BaseEstimativa t : trechos) {
            if (t.desde() == null || !competencia.antesDe(t.desde())) {
                vale = t;
            }
        }
        return vale;
    }
}
