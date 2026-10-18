package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Periodicidade;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.service.CalculadoraImpacto.Impacto;
import br.com.familianca.domain.vo.Dinheiro;

import java.util.Map;

/** O diferencial do produto: o ano inteiro da família e o peso de cada gasto nele. Qualquer membro. */
public interface ConsultarVisaoAnual {

    VisaoAnualDetalhada visaoAnual(int ano);

    /** Quanto um gasto representa no ano, comparado à renda e às despesas da família no ano (nulo = atual). */
    Impacto simularImpacto(Dinheiro valor, Periodicidade periodicidade, Integer ano);

    int anoAtual();

    /** membros: todos da família (inclusive inativos), por id, para dar nome às linhas por pessoa. */
    record VisaoAnualDetalhada(VisaoAnual visao, Map<Long, Membro> membros) {
    }
}
