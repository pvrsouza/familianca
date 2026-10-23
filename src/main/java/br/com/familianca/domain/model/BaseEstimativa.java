package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.util.List;

/**
 * De onde saiu a estimativa dos próximos meses, para a tela mostrar a conta: a regra, de quem é a regra e os meses
 * (ou confirmações) que entraram, com o valor de cada um.
 */
public record BaseEstimativa(Estimativa regra, Origem origem, List<Mes> meses, Dinheiro valor) {

    /** De quem é a regra usada: a própria conta que se repete, a categoria ou o padrão da família. */
    public enum Origem { DESPESA, CATEGORIA, FAMILIA }

    public record Mes(Competencia competencia, Dinheiro valor) {
    }

    public BaseEstimativa {
        meses = List.copyOf(meses);
    }
}
