package br.com.familianca.domain.service;

import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.model.Variacao;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.model.VisaoAnual.Fluxo;
import br.com.familianca.domain.model.VisaoAnual.LinhaCategoria;
import br.com.familianca.domain.model.VisaoAnual.LinhaGrupo;
import br.com.familianca.domain.model.VisaoAnual.LinhaMembro;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta a {@link VisaoAnual}: soma os lançamentos do ano, projeta as recorrências não confirmadas como previstas,
 * estima os próximos meses das despesas que não se repetem, calcula as categorias por alíquota sobre as receitas
 * tributáveis e encadeia o fluxo de caixa mês a mês. Categoria ou grupo desativado só aparece se tiver valor no ano.
 */
public final class CalculadoraVisaoAnual {

    /** Ocorrência de recorrência já confirmada (virou lançamento), em qualquer data. */
    public record Ocorrencia(Long recorrenciaId, Competencia competencia) {
    }

    /**
     * @param lancamentos        lançamentos com data no ano
     * @param confirmadas        ocorrências do ano já confirmadas
     * @param aplicacoes         aportes líquidos nos investimentos por mês
     * @param rendaInvestimentos rendimento dos investimentos por mês
     * @param metaAporte         % da sobra (renda − despesas) que a família quer investir; nulo = sem meta
     * @param estimativaFamilia  regra padrão da família para estimar os próximos meses
     * @param hoje               data de referência: só o ano corrente é estimado, a partir do mês seguinte
     */
    public record Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                          List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                          SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte,
                          Estimativa estimativaFamilia, LocalDate hoje) {

        /** Sem estimativa dos próximos meses. */
        public Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                       List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                       SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte) {
            this(ano, grupos, categorias, lancamentos, recorrencias, confirmadas, saldos, aplicacoes,
                    rendaInvestimentos, metaAporte, Estimativa.NENHUMA, null);
        }
    }

    public VisaoAnual calcular(Entrada e) {
        Map<Long, Grupo> grupos = e.grupos().stream().collect(Collectors.toMap(Grupo::id, Function.identity()));
        Map<Long, Categoria> categorias = e.categorias().stream()
                .filter(c -> grupos.containsKey(c.grupoId()))
                .collect(Collectors.toMap(Categoria::id, Function.identity()));

        Map<Long, SerieMensal> realizado = new HashMap<>();
        Map<Long, SerieMensal> previsto = new HashMap<>();
        Map<Long, SerieMensal[]> porMembro = new LinkedHashMap<>();

        for (Lancamento l : e.lancamentos()) {
            Categoria c = categorias.get(l.categoriaId());
            if (c == null || l.data().getYear() != e.ano()) {
                continue;
            }
            int mes = l.data().getMonthValue();
            realizado.merge(c.id(), SerieMensal.ZERO.comMes(mes, l.valor()), SerieMensal::somar);
            somarMembro(porMembro, l.membroId(), grupos.get(c.grupoId()).tipo(), mes, l.valor());
        }
        for (Recorrencia r : e.recorrencias()) {
            Categoria c = categorias.get(r.categoriaId());
            if (c == null) {
                continue;
            }
            for (Competencia comp : r.ocorrenciasNoAno(e.ano())) {
                if (e.confirmadas().contains(new Ocorrencia(r.id(), comp))) {
                    continue;
                }
                previsto.merge(c.id(), SerieMensal.ZERO.comMes(comp.numeroMes(), r.valor()), SerieMensal::somar);
                somarMembro(porMembro, r.membroId(), grupos.get(c.grupoId()).tipo(), comp.numeroMes(), r.valor());
            }
        }

        Map<Long, SerieMensal> estimado = estimar(e, categorias, grupos, realizado, previsto);
        estimado.forEach((id, serie) -> previsto.merge(id, serie, SerieMensal::somar));

        // Base das alíquotas: receitas tributáveis, separando o realizado do previsto.
        SerieMensal baseRealizada = SerieMensal.ZERO;
        SerieMensal basePrevista = SerieMensal.ZERO;
        for (Categoria c : categorias.values()) {
            if (c.tributavel()) {
                baseRealizada = baseRealizada.somar(realizado.getOrDefault(c.id(), SerieMensal.ZERO));
                basePrevista = basePrevista.somar(previsto.getOrDefault(c.id(), SerieMensal.ZERO));
            }
        }
        for (Categoria c : categorias.values()) {
            if (c.calculada()) {
                SerieMensal calcReal = aplicar(c.aliquota(), baseRealizada);
                SerieMensal calcPrev = aplicar(c.aliquota(), basePrevista);
                realizado.put(c.id(), calcReal);
                previsto.put(c.id(), calcPrev);
                for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
                    somarMembro(porMembro, null, TipoMovimento.DESPESA, mes,
                            calcReal.mes(mes).somar(calcPrev.mes(mes)));
                }
            }
        }

        Map<TipoMovimento, List<LinhaCategoria>> linhasPorTipo = linhasCategoria(categorias.values(), grupos,
                realizado, previsto, estimado);
        Dinheiro totalTributavelAno = baseRealizada.somar(basePrevista).total();
        List<LinhaGrupo> receitas = linhasGrupo(TipoMovimento.RECEITA, grupos, linhasPorTipo, totalTributavelAno);
        List<LinhaGrupo> despesas = linhasGrupo(TipoMovimento.DESPESA, grupos, linhasPorTipo, totalTributavelAno);

        Fluxo fluxo = fluxo(e, receitas, despesas);
        return new VisaoAnual(e.ano(), receitas, despesas, fluxo, Variacao.de(fluxo.despesasTotal()),
                membros(porMembro));
    }

    /**
     * Estimativa dos próximos meses (só no ano corrente, só despesa que não é recorrente nem calculada, ativa): a
     * média, o máximo ou o mínimo dos meses que já passaram, contados a partir do primeiro mês do ano com algum
     * lançamento da família (antes disso a família ainda não usava o app). Cada mês futuro recebe só o que falta
     * para chegar na estimativa, descontando o que já foi lançado ou está previsto ali (parcelas, recorrências).
     * O mês corrente não é estimado: está em andamento. A estimativa não entra na visão por pessoa.
     */
    private static Map<Long, SerieMensal> estimar(Entrada e, Map<Long, Categoria> categorias, Map<Long, Grupo> grupos,
                                                  Map<Long, SerieMensal> realizado, Map<Long, SerieMensal> previsto) {
        Map<Long, SerieMensal> estimado = new HashMap<>();
        if (e.hoje() == null || e.hoje().getYear() != e.ano() || e.estimativaFamilia() == null) {
            return estimado;
        }
        int mesAtual = e.hoje().getMonthValue();
        int inicio = e.lancamentos().stream()
                .filter(l -> l.data().getYear() == e.ano() && l.data().getMonthValue() < mesAtual)
                .mapToInt(l -> l.data().getMonthValue()).min().orElse(0);
        if (inicio == 0) {
            return estimado;
        }
        for (Categoria c : categorias.values()) {
            Grupo g = grupos.get(c.grupoId());
            Estimativa regra = c.estimativaOu(e.estimativaFamilia());
            if (g.tipo() != TipoMovimento.DESPESA || c.recorrente() || c.calculada() || !c.ativa() || !g.ativo()
                    || regra == Estimativa.NENHUMA) {
                continue;
            }
            SerieMensal real = realizado.getOrDefault(c.id(), SerieMensal.ZERO);
            List<Dinheiro> passados = new ArrayList<>();
            for (int mes = inicio; mes < mesAtual; mes++) {
                passados.add(real.mes(mes));
            }
            Dinheiro base = switch (regra) {
                case MEDIA -> passados.stream().reduce(Dinheiro.ZERO, Dinheiro::somar).proporcional(1, passados.size());
                case MAXIMO -> passados.stream().max(Comparator.naturalOrder()).orElse(Dinheiro.ZERO);
                case MINIMO -> passados.stream().min(Comparator.naturalOrder()).orElse(Dinheiro.ZERO);
                case NENHUMA -> Dinheiro.ZERO;
            };
            if (!base.ehPositivo()) {
                continue;
            }
            SerieMensal prev = previsto.getOrDefault(c.id(), SerieMensal.ZERO);
            SerieMensal serie = SerieMensal.ZERO;
            for (int mes = mesAtual + 1; mes <= SerieMensal.MESES; mes++) {
                Dinheiro falta = base.subtrair(real.mes(mes)).subtrair(prev.mes(mes));
                if (falta.ehPositivo()) {
                    serie = serie.comMes(mes, falta);
                }
            }
            if (!serie.ehZero()) {
                estimado.put(c.id(), serie);
            }
        }
        return estimado;
    }

    private static SerieMensal aplicar(Percentual aliquota, SerieMensal base) {
        List<Dinheiro> meses = base.meses().stream().map(aliquota::aplicarSobre).toList();
        return new SerieMensal(meses);
    }

    private static void somarMembro(Map<Long, SerieMensal[]> porMembro, Long membroId, TipoMovimento tipo, int mes,
                                    Dinheiro valor) {
        if (valor.ehZero()) {
            return;
        }
        SerieMensal[] series = porMembro.computeIfAbsent(membroId, k -> new SerieMensal[]{SerieMensal.ZERO, SerieMensal.ZERO});
        int i = tipo == TipoMovimento.RECEITA ? 0 : 1;
        series[i] = series[i].somarNoMes(mes, valor);
    }

    private static Map<TipoMovimento, List<LinhaCategoria>> linhasCategoria(
            Iterable<Categoria> categorias, Map<Long, Grupo> grupos, Map<Long, SerieMensal> realizado,
            Map<Long, SerieMensal> previsto, Map<Long, SerieMensal> estimado) {
        Map<TipoMovimento, List<LinhaCategoria>> porTipo = new HashMap<>();
        Map<TipoMovimento, Dinheiro> totalTipo = new HashMap<>();
        List<LinhaCategoria> provisorias = new ArrayList<>();
        for (Categoria c : categorias) {
            Grupo g = grupos.get(c.grupoId());
            SerieMensal real = realizado.getOrDefault(c.id(), SerieMensal.ZERO);
            SerieMensal prev = previsto.getOrDefault(c.id(), SerieMensal.ZERO);
            boolean visivel = (c.ativa() && g.ativo()) || !real.ehZero() || !prev.ehZero();
            if (visivel) {
                provisorias.add(new LinhaCategoria(c, real, prev, estimado.getOrDefault(c.id(), SerieMensal.ZERO),
                        null, null));
                totalTipo.merge(g.tipo(), real.somar(prev).total(), Dinheiro::somar);
            }
        }
        for (TipoMovimento tipo : TipoMovimento.values()) {
            Dinheiro total = totalTipo.getOrDefault(tipo, Dinheiro.ZERO);
            List<LinhaCategoria> doTipo = provisorias.stream()
                    .filter(l -> grupos.get(l.categoria().grupoId()).tipo() == tipo)
                    .sorted(Comparator.comparing((LinhaCategoria l) -> l.total().total()).reversed())
                    .toList();
            List<LinhaCategoria> finais = new ArrayList<>();
            int posicao = 0;
            for (LinhaCategoria l : doTipo) {
                Dinheiro t = l.total().total();
                Integer rank = t.ehZero() ? null : ++posicao;
                finais.add(new LinhaCategoria(l.categoria(), l.realizado(), l.previsto(), l.estimado(),
                        Percentual.razao(t, total),
                        rank));
            }
            porTipo.put(tipo, finais);
        }
        return porTipo;
    }

    private static List<LinhaGrupo> linhasGrupo(TipoMovimento tipo, Map<Long, Grupo> grupos,
                                                Map<TipoMovimento, List<LinhaCategoria>> linhasPorTipo,
                                                Dinheiro totalTributavelAno) {
        List<LinhaCategoria> linhas = linhasPorTipo.get(tipo);
        Dinheiro totalTipo = linhas.stream().map(l -> l.total().total()).reduce(Dinheiro.ZERO, Dinheiro::somar);
        Comparator<LinhaCategoria> ordemCategoria = Comparator
                .comparingInt((LinhaCategoria l) -> l.categoria().ordem())
                .thenComparing(l -> l.categoria().nome());
        return grupos.values().stream()
                .filter(g -> g.tipo() == tipo)
                .sorted(Comparator.comparingInt(Grupo::ordem).thenComparing(Grupo::nome))
                .map(g -> {
                    List<LinhaCategoria> doGrupo = linhas.stream()
                            .filter(l -> l.categoria().grupoId().equals(g.id()))
                            .sorted(ordemCategoria)
                            .toList();
                    SerieMensal real = doGrupo.stream().map(LinhaCategoria::realizado)
                            .reduce(SerieMensal.ZERO, SerieMensal::somar);
                    SerieMensal prev = doGrupo.stream().map(LinhaCategoria::previsto)
                            .reduce(SerieMensal.ZERO, SerieMensal::somar);
                    if (!g.ativo() && real.somar(prev).ehZero()) {
                        return null;
                    }
                    SerieMensal total = real.somar(prev);
                    return new LinhaGrupo(g, doGrupo, real, prev, Percentual.razao(total.total(), totalTipo),
                            Variacao.de(total),
                            g.empresa() ? Percentual.razao(total.total(), totalTributavelAno) : null);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static Fluxo fluxo(Entrada e, List<LinhaGrupo> receitas, List<LinhaGrupo> despesas) {
        SerieMensal receitasFixas = somaPorNatureza(receitas, Natureza.FIXA);
        SerieMensal receitasVariaveis = somaPorNatureza(receitas, Natureza.VARIAVEL);
        SerieMensal despesasFixas = somaPorNatureza(despesas, Natureza.FIXA);
        SerieMensal despesasVariaveis = somaPorNatureza(despesas, Natureza.VARIAVEL);
        Map<Competencia, Dinheiro> saldos = e.saldos().stream()
                .collect(Collectors.toMap(SaldoMes::competencia, SaldoMes::valor));

        SerieMensal sobra = SerieMensal.ZERO;
        List<Boolean> informada = new ArrayList<>();
        Dinheiro fluxoAnterior = Dinheiro.ZERO;
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            Dinheiro saldo = saldos.get(Competencia.of(e.ano(), mes));
            informada.add(saldo != null);
            Dinheiro sobraMes = saldo != null ? saldo : fluxoAnterior;
            sobra = sobra.comMes(mes, sobraMes);
            Dinheiro renda = receitasFixas.mes(mes).somar(receitasVariaveis.mes(mes));
            Dinheiro gastos = despesasFixas.mes(mes).somar(despesasVariaveis.mes(mes));
            fluxoAnterior = sobraMes.somar(renda).subtrair(e.aplicacoes().mes(mes)).subtrair(gastos);
        }

        List<Percentual> grau = new ArrayList<>();
        SerieMensal aportePrevisto = SerieMensal.ZERO;
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            grau.add(Percentual.razao(e.rendaInvestimentos().mes(mes), despesasFixas.mes(mes)));
            if (e.metaAporte() != null) {
                Dinheiro sobraDoMes = receitasFixas.mes(mes).somar(receitasVariaveis.mes(mes))
                        .subtrair(despesasFixas.mes(mes)).subtrair(despesasVariaveis.mes(mes));
                aportePrevisto = aportePrevisto.comMes(mes, e.metaAporte().aplicarSobre(sobraDoMes.maximo(Dinheiro.ZERO)));
            }
        }
        return new Fluxo(sobra, informada, receitasFixas, receitasVariaveis, e.aplicacoes(), despesasFixas,
                despesasVariaveis, e.rendaInvestimentos(), grau, aportePrevisto);
    }

    private static SerieMensal somaPorNatureza(List<LinhaGrupo> grupos, Natureza natureza) {
        return grupos.stream().filter(g -> g.grupo().natureza() == natureza)
                .map(LinhaGrupo::total).reduce(SerieMensal.ZERO, SerieMensal::somar);
    }

    private static List<LinhaMembro> membros(Map<Long, SerieMensal[]> porMembro) {
        Dinheiro totalReceitas = porMembro.values().stream().map(s -> s[0].total()).reduce(Dinheiro.ZERO, Dinheiro::somar);
        Dinheiro totalDespesas = porMembro.values().stream().map(s -> s[1].total()).reduce(Dinheiro.ZERO, Dinheiro::somar);
        return porMembro.entrySet().stream()
                .map(en -> new LinhaMembro(en.getKey(), en.getValue()[0], en.getValue()[1],
                        Percentual.razao(en.getValue()[0].total(), totalReceitas),
                        Percentual.razao(en.getValue()[1].total(), totalDespesas)))
                .sorted(Comparator.comparing((LinhaMembro m) -> m.despesas().total()).reversed())
                .toList();
    }
}
