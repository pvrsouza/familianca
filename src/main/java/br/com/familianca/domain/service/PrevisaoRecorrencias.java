package br.com.familianca.domain.service;

import br.com.familianca.domain.model.BaseEstimativa;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Valor previsto das ocorrências de uma despesa que se repete, pela regra de estimativa (da própria conta, da
 * categoria ou da família, nessa ordem): conta de valor variável (DAS, energia, água) fica prevista pela média das 3
 * últimas confirmações (mesmo do ano anterior), pela média, pelo maior ou pelo menor valor confirmado no ano, em vez do
 * valor cadastrado. Vale do mês atual em diante; as ocorrências passadas não confirmadas continuam com o valor
 * cadastrado. Fica de fora: receita, recorrência que confirma sozinha (valor fixo por definição), regra NENHUMA e
 * recorrência ainda sem confirmação na base desde a última mudança de valor (vale o cadastrado).
 * Nada fica gravado: é calculado a cada consulta, então mudar a regra ou um valor confirmado recalcula na hora.
 */
public final class PrevisaoRecorrencias {

    /**
     * @param valor        o que se prevê para cada ocorrência do mês atual em diante
     * @param regra        a regra usada
     * @param confirmacoes quantas confirmações entraram na conta
     * @param base         a regra, de quem ela é e as confirmações usadas (competência e valor)
     */
    public record Previsao(Dinheiro valor, Estimativa regra, int confirmacoes, BaseEstimativa base) {
    }

    private final Map<Long, Previsao> previsoes;
    private final Competencia mesAtual;

    private PrevisaoRecorrencias(Map<Long, Previsao> previsoes, Competencia mesAtual) {
        this.previsoes = previsoes;
        this.mesAtual = mesAtual;
    }

    /** Sem estimativa: toda ocorrência vale o cadastrado. */
    public static PrevisaoRecorrencias nenhuma() {
        return new PrevisaoRecorrencias(Map.of(), null);
    }

    /**
     * @param confirmadas lançamentos de ocorrências confirmadas dos últimos 12 meses (ou mais) até {@code hoje}
     */
    public static PrevisaoRecorrencias calcular(List<Recorrencia> recorrencias, List<Categoria> categorias,
                                                List<Grupo> grupos, List<Lancamento> confirmadas,
                                                Estimativa daFamilia, LocalDate hoje) {
        Competencia mesAtual = Competencia.de(hoje);
        Map<Long, Categoria> porId = categorias.stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
        Map<Long, Grupo> gruposPorId = grupos.stream().collect(Collectors.toMap(Grupo::id, Function.identity()));
        Map<Long, List<Lancamento>> porRecorrencia = confirmadas.stream()
                .filter(l -> l.recorrenciaId() != null && l.competenciaRecorrencia() != null
                        && l.competenciaRecorrencia().antesDe(mesAtual))
                .collect(Collectors.groupingBy(Lancamento::recorrenciaId));
        Map<Long, Previsao> previsoes = new HashMap<>();
        for (Recorrencia r : recorrencias) {
            Categoria c = porId.get(r.categoriaId());
            Grupo g = c != null ? gruposPorId.get(c.grupoId()) : null;
            if (g == null || g.tipo() != TipoMovimento.DESPESA || r.confirmaSozinha()) {
                continue;
            }
            Estimativa regra = r.estimativa() != null ? r.estimativa() : c.estimativaOu(daFamilia);
            BaseEstimativa.Origem origem = r.estimativa() != null ? BaseEstimativa.Origem.DESPESA
                    : c.estimativa() != null ? BaseEstimativa.Origem.CATEGORIA : BaseEstimativa.Origem.FAMILIA;
            if (regra == null || regra == Estimativa.NENHUMA) {
                continue;
            }
            List<Lancamento> usadas = porRecorrencia.getOrDefault(r.id(), List.of()).stream()
                    .filter(l -> r.valorDesde() == null || !l.competenciaRecorrencia().antesDe(r.valorDesde()))
                    .filter(l -> !regra.doAno() || l.competenciaRecorrencia().ano() == hoje.getYear())
                    .sorted(Comparator.comparing(Lancamento::competenciaRecorrencia))
                    .toList();
            if (!regra.doAno() && usadas.size() > Estimativa.MESES_RECENTES) {
                usadas = usadas.subList(usadas.size() - Estimativa.MESES_RECENTES, usadas.size());
            }
            if (usadas.isEmpty()) {
                continue;
            }
            Dinheiro valor = regra.aplicar(usadas.stream().map(Lancamento::valor).toList());
            List<BaseEstimativa.Mes> meses = usadas.stream()
                    .map(l -> new BaseEstimativa.Mes(l.competenciaRecorrencia(), l.valor())).toList();
            previsoes.put(r.id(), new Previsao(valor, regra, usadas.size(),
                    new BaseEstimativa(regra, origem, meses, valor)));
        }
        return new PrevisaoRecorrencias(previsoes, mesAtual);
    }

    /**
     * O valor previsto da ocorrência na competência: o ajustado só daquele mês; senão a estimativa do mês atual em
     * diante; senão o cadastrado.
     */
    public Dinheiro valorEm(Recorrencia r, Competencia competencia) {
        Dinheiro ajustado = r.valorAjustadoEm(competencia);
        if (ajustado != null) {
            return ajustado;
        }
        Previsao p = previsoes.get(r.id());
        return p != null && !competencia.antesDe(mesAtual) ? p.valor() : r.valor();
    }

    /** A estimativa da recorrência, se houver. */
    public Previsao de(Recorrencia r) {
        return previsoes.get(r.id());
    }
}
