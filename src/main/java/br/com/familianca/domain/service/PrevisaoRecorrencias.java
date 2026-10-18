package br.com.familianca.domain.service;

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
 * Valor previsto das ocorrências de uma despesa que se repete, pela regra de estimativa (da categoria ou da família):
 * conta de valor variável (DAS, energia, água) fica prevista pela média, pelo maior ou pelo menor valor confirmado no
 * ano, em vez do valor cadastrado. Vale do mês atual em diante; as ocorrências passadas não confirmadas continuam com
 * o valor cadastrado. Fica de fora: receita, recorrência que confirma sozinha (valor fixo por definição), regra
 * NENHUMA e recorrência ainda sem confirmação no ano desde a última mudança de valor (vale o cadastrado).
 * Nada fica gravado: é calculado a cada consulta, então mudar a regra ou um valor confirmado recalcula na hora.
 */
public final class PrevisaoRecorrencias {

    /**
     * @param valor        o que se prevê para cada ocorrência do mês atual em diante
     * @param regra        a regra usada (MEDIA, MAXIMO ou MINIMO)
     * @param confirmacoes quantas confirmações do ano entraram na conta
     */
    public record Previsao(Dinheiro valor, Estimativa regra, int confirmacoes) {
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
     * @param confirmadasDoAno lançamentos de ocorrências confirmadas com competência no ano de {@code hoje}
     */
    public static PrevisaoRecorrencias calcular(List<Recorrencia> recorrencias, List<Categoria> categorias,
                                                List<Grupo> grupos, List<Lancamento> confirmadasDoAno,
                                                Estimativa daFamilia, LocalDate hoje) {
        Competencia mesAtual = Competencia.de(hoje);
        Map<Long, Categoria> porId = categorias.stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
        Map<Long, Grupo> gruposPorId = grupos.stream().collect(Collectors.toMap(Grupo::id, Function.identity()));
        Map<Long, List<Lancamento>> porRecorrencia = confirmadasDoAno.stream()
                .filter(l -> l.recorrenciaId() != null && l.competenciaRecorrencia() != null
                        && l.competenciaRecorrencia().ano() == hoje.getYear()
                        && l.competenciaRecorrencia().antesDe(mesAtual))
                .collect(Collectors.groupingBy(Lancamento::recorrenciaId));
        Map<Long, Previsao> previsoes = new HashMap<>();
        for (Recorrencia r : recorrencias) {
            Categoria c = porId.get(r.categoriaId());
            Grupo g = c != null ? gruposPorId.get(c.grupoId()) : null;
            if (g == null || g.tipo() != TipoMovimento.DESPESA || r.confirmaSozinha()) {
                continue;
            }
            Estimativa regra = c.estimativaOu(daFamilia);
            if (regra == null || regra == Estimativa.NENHUMA) {
                continue;
            }
            List<Dinheiro> valores = porRecorrencia.getOrDefault(r.id(), List.of()).stream()
                    .filter(l -> r.valorDesde() == null || !l.competenciaRecorrencia().antesDe(r.valorDesde()))
                    .map(Lancamento::valor)
                    .toList();
            if (valores.isEmpty()) {
                continue;
            }
            Dinheiro valor = switch (regra) {
                case MEDIA -> valores.stream().reduce(Dinheiro.ZERO, Dinheiro::somar).proporcional(1, valores.size());
                case MAXIMO -> valores.stream().max(Comparator.naturalOrder()).orElseThrow();
                case MINIMO -> valores.stream().min(Comparator.naturalOrder()).orElseThrow();
                case NENHUMA -> r.valor();
            };
            previsoes.put(r.id(), new Previsao(valor, regra, valores.size()));
        }
        return new PrevisaoRecorrencias(previsoes, mesAtual);
    }

    /** O valor previsto da ocorrência na competência: a estimativa do mês atual em diante, senão o cadastrado. */
    public Dinheiro valorEm(Recorrencia r, Competencia competencia) {
        Previsao p = previsoes.get(r.id());
        return p != null && !competencia.antesDe(mesAtual) ? p.valor() : r.valor();
    }

    /** A estimativa da recorrência, se houver. */
    public Previsao de(Recorrencia r) {
        return previsoes.get(r.id());
    }
}
