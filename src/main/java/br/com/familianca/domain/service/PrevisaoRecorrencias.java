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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Valor previsto das ocorrências de uma despesa que se repete, pela regra de estimativa (da própria conta, da
 * categoria ou da família, nessa ordem): conta de valor variável (DAS, energia, água) fica prevista pela média das 3
 * últimas confirmações (mesmo do ano anterior), pela média, pelo maior ou pelo menor valor confirmado no ano, em vez do
 * valor cadastrado. Vale do mês atual em diante; as ocorrências passadas não confirmadas continuam com o valor
 * cadastrado. Entra toda confirmação (conta confirmada é valor real e fechado), inclusive a do mês atual e as pagas
 * adiantado; MEDIA_3M usa as 3 de competência mais recente. Fica de fora:
 * receita, recorrência que confirma sozinha (valor fixo por definição), regra NENHUMA e recorrência ainda sem
 * confirmação na base desde a última mudança de valor (vale o cadastrado).
 * A regra pode mudar a partir de um mês futuro (da conta ou da categoria): a previsão vira trechos, um por regra.
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

    private final Map<Long, List<BaseEstimativa>> trechos;
    private final Competencia mesAtual;

    private PrevisaoRecorrencias(Map<Long, List<BaseEstimativa>> trechos, Competencia mesAtual) {
        this.trechos = trechos;
        this.mesAtual = mesAtual;
    }

    /** Sem estimativa: toda ocorrência vale o cadastrado. */
    public static PrevisaoRecorrencias nenhuma() {
        return new PrevisaoRecorrencias(Map.of(), null);
    }

    /**
     * @param confirmadas lançamentos de ocorrências confirmadas dos últimos 12 meses (ou mais), inclusive as adiantadas
     */
    public static PrevisaoRecorrencias calcular(List<Recorrencia> recorrencias, List<Categoria> categorias,
                                                List<Grupo> grupos, List<Lancamento> confirmadas,
                                                Estimativa daFamilia, LocalDate hoje) {
        Competencia mesAtual = Competencia.de(hoje);
        Map<Long, Categoria> porId = categorias.stream().collect(Collectors.toMap(Categoria::id, Function.identity()));
        Map<Long, Grupo> gruposPorId = grupos.stream().collect(Collectors.toMap(Grupo::id, Function.identity()));
        Map<Long, List<Lancamento>> porRecorrencia = confirmadas.stream()
                .filter(l -> l.recorrenciaId() != null && l.competenciaRecorrencia() != null)
                .collect(Collectors.groupingBy(Lancamento::recorrenciaId));
        Map<Long, List<BaseEstimativa>> trechos = new HashMap<>();
        for (Recorrencia r : recorrencias) {
            Categoria c = porId.get(r.categoriaId());
            Grupo g = c != null ? gruposPorId.get(c.grupoId()) : null;
            if (g == null || g.tipo() != TipoMovimento.DESPESA || r.confirmaSozinha() || daFamilia == null) {
                continue;
            }
            List<Lancamento> daConta = porRecorrencia.getOrDefault(r.id(), List.of()).stream()
                    .filter(l -> r.valorDesde() == null || !l.competenciaRecorrencia().antesDe(r.valorDesde()))
                    .sorted(Comparator.comparing(Lancamento::competenciaRecorrencia))
                    .toList();
            // Um trecho no mês atual e outro em cada mês futuro em que a regra (da conta ou da categoria) muda.
            List<Competencia> inicios = Stream.of(mesAtual, r.estimativaDesde(), c.estimativaDesde())
                    .filter(m -> m != null && !m.antesDe(mesAtual)).distinct().sorted().toList();
            List<BaseEstimativa> daRecorrencia = new ArrayList<>();
            for (Competencia desde : inicios) {
                Estimativa propria = r.estimativaEm(desde);
                Estimativa daCategoria = c.estimativaEm(desde);
                Estimativa regra = propria != null ? propria : daCategoria != null ? daCategoria : daFamilia;
                BaseEstimativa.Origem origem = propria != null ? BaseEstimativa.Origem.DESPESA
                        : daCategoria != null ? BaseEstimativa.Origem.CATEGORIA : BaseEstimativa.Origem.FAMILIA;
                BaseEstimativa ultimo = daRecorrencia.isEmpty() ? null : daRecorrencia.getLast();
                if (ultimo == null || ultimo.regra() != regra || ultimo.origem() != origem) {
                    daRecorrencia.add(base(regra, origem, desde, daConta, hoje));
                }
            }
            trechos.put(r.id(), List.copyOf(daRecorrencia));
        }
        return new PrevisaoRecorrencias(trechos, mesAtual);
    }

    /** A regra aplicada às confirmações da conta; sem confirmação na base (ou NENHUMA), o valor fica nulo. */
    private static BaseEstimativa base(Estimativa regra, BaseEstimativa.Origem origem, Competencia desde,
                                       List<Lancamento> daConta, LocalDate hoje) {
        List<Lancamento> usadas = regra == Estimativa.NENHUMA ? List.of() : daConta.stream()
                .filter(l -> !regra.doAno() || l.competenciaRecorrencia().ano() == hoje.getYear())
                .toList();
        if (!regra.doAno() && usadas.size() > Estimativa.MESES_RECENTES) {
            usadas = usadas.subList(usadas.size() - Estimativa.MESES_RECENTES, usadas.size());
        }
        List<BaseEstimativa.Mes> meses = usadas.stream()
                .map(l -> new BaseEstimativa.Mes(l.competenciaRecorrencia(), l.valor())).toList();
        Dinheiro valor = usadas.isEmpty() ? null : regra.aplicar(usadas.stream().map(Lancamento::valor).toList());
        return new BaseEstimativa(regra, origem, meses, valor, desde);
    }

    /**
     * O valor previsto da ocorrência na competência: o ajustado só daquele mês; senão a estimativa do trecho daquele
     * mês (do mês atual em diante); senão o cadastrado.
     */
    public Dinheiro valorEm(Recorrencia r, Competencia competencia) {
        Dinheiro ajustado = r.valorAjustadoEm(competencia);
        if (ajustado != null) {
            return ajustado;
        }
        BaseEstimativa trecho = mesAtual == null || competencia.antesDe(mesAtual) ? null
                : BaseEstimativa.em(trechos.getOrDefault(r.id(), List.of()), competencia);
        return trecho != null && trecho.estima() ? trecho.valor() : r.valor();
    }

    /** A estimativa da recorrência no mês atual, se houver. */
    public Previsao de(Recorrencia r) {
        List<BaseEstimativa> t = trechos.getOrDefault(r.id(), List.of());
        if (t.isEmpty() || !t.getFirst().estima()) {
            return null;
        }
        BaseEstimativa b = t.getFirst();
        return new Previsao(b.valor(), b.regra(), b.meses().size(), b);
    }

    /**
     * Como a recorrência é prevista do mês atual em diante: um trecho por regra, em ordem (vazio = vale o cadastrado,
     * como em receita e conta que confirma sozinha).
     */
    public List<BaseEstimativa> trechos(Recorrencia r) {
        return trechos.getOrDefault(r.id(), List.of());
    }
}
