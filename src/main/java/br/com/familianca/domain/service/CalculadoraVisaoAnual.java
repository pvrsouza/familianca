package br.com.familianca.domain.service;

import br.com.familianca.domain.model.BaseEstimativa;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.MetasAporte;
import br.com.familianca.domain.model.MovimentoDoMes;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.Recorrencia;
import br.com.familianca.domain.model.SaldoMes;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.model.Variacao;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.model.VisaoAnual.Fluxo;
import br.com.familianca.domain.model.VisaoAnual.LinhaCategoria;
import br.com.familianca.domain.model.VisaoAnual.LinhaGrupo;
import br.com.familianca.domain.model.VisaoAnual.LinhaItem;
import br.com.familianca.domain.model.VisaoAnual.LinhaMembro;
import br.com.familianca.domain.model.VisaoAnual.MetaAporteMes;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Monta a {@link VisaoAnual}: soma os lançamentos do ano, projeta as recorrências não confirmadas como previstas,
 * estima os próximos meses das despesas que não se repetem, calcula as categorias por alíquota sobre as receitas
 * tributáveis e encadeia o fluxo de caixa mês a mês. Categoria ou grupo desativado só aparece se tiver valor no ano.
 */
public final class CalculadoraVisaoAnual {

    private static final CalculadoraMetaAporte META_APORTE = new CalculadoraMetaAporte();

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
     * @param previsao           valor previsto de cada ocorrência das recorrências (estimado pela regra ou cadastrado)
     * @param sobraAnoAnterior   fluxo líquido de dezembro do ano anterior, com que janeiro começa sem saldo informado;
     *                           nulo = nenhum saldo informado antes deste ano (janeiro começa do zero)
     * @param anteriores         lançamentos do ano anterior: a média dos últimos meses cruza o ano (em janeiro, usa
     *                           outubro a dezembro)
     * @param metasAporte        metas definidas por mês e regra para estimar as outras; a meta de partida é
     *                           {@code metaAporte}. Nulo = só a meta de partida, em todo mês
     */
    public record Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                          List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                          SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte,
                          Estimativa estimativaFamilia, LocalDate hoje, PrevisaoRecorrencias previsao,
                          Dinheiro sobraAnoAnterior, List<Lancamento> anteriores, MetasAporte metasAporte) {

        public Entrada {
            anteriores = anteriores == null ? List.of() : List.copyOf(anteriores);
            metasAporte = metasAporte == null ? MetasAporte.NENHUMA : metasAporte;
        }

        /** Só a meta de partida, em todo mês. */
        public Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                       List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                       SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte,
                       Estimativa estimativaFamilia, LocalDate hoje, PrevisaoRecorrencias previsao,
                       Dinheiro sobraAnoAnterior, List<Lancamento> anteriores) {
            this(ano, grupos, categorias, lancamentos, recorrencias, confirmadas, saldos, aplicacoes,
                    rendaInvestimentos, metaAporte, estimativaFamilia, hoje, previsao, sobraAnoAnterior, anteriores,
                    null);
        }

        /** Sem os lançamentos do ano anterior. */
        public Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                       List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                       SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte,
                       Estimativa estimativaFamilia, LocalDate hoje, PrevisaoRecorrencias previsao,
                       Dinheiro sobraAnoAnterior) {
            this(ano, grupos, categorias, lancamentos, recorrencias, confirmadas, saldos, aplicacoes,
                    rendaInvestimentos, metaAporte, estimativaFamilia, hoje, previsao, sobraAnoAnterior, List.of());
        }

        /** Sem saldo informado em anos anteriores. */
        public Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                       List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                       SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte,
                       Estimativa estimativaFamilia, LocalDate hoje, PrevisaoRecorrencias previsao) {
            this(ano, grupos, categorias, lancamentos, recorrencias, confirmadas, saldos, aplicacoes,
                    rendaInvestimentos, metaAporte, estimativaFamilia, hoje, previsao, null);
        }

        /** Recorrências sempre pelo valor cadastrado. */
        public Entrada(int ano, List<Grupo> grupos, List<Categoria> categorias, List<Lancamento> lancamentos,
                       List<Recorrencia> recorrencias, Set<Ocorrencia> confirmadas, List<SaldoMes> saldos,
                       SerieMensal aplicacoes, SerieMensal rendaInvestimentos, Percentual metaAporte,
                       Estimativa estimativaFamilia, LocalDate hoje) {
            this(ano, grupos, categorias, lancamentos, recorrencias, confirmadas, saldos, aplicacoes,
                    rendaInvestimentos, metaAporte, estimativaFamilia, hoje, PrevisaoRecorrencias.nenhuma());
        }

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
        Itens itens = new Itens(e.recorrencias(), e.previsao());

        for (Lancamento l : e.lancamentos()) {
            Categoria c = categorias.get(l.categoriaId());
            if (c == null || l.data().getYear() != e.ano()) {
                continue;
            }
            int mes = l.data().getMonthValue();
            realizado.merge(c.id(), SerieMensal.ZERO.comMes(mes, l.valor()), SerieMensal::somar);
            itens.realizado(c.id(), l, mes);
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
                Dinheiro valor = e.previsao().valorEm(r, comp);
                previsto.merge(c.id(), SerieMensal.ZERO.comMes(comp.numeroMes(), valor), SerieMensal::somar);
                itens.previsto(c.id(), r, comp.numeroMes(), valor);
                somarMembro(porMembro, r.membroId(), grupos.get(c.grupoId()).tipo(), comp.numeroMes(), valor);
            }
        }

        Map<Long, List<BaseEstimativa>> bases = new HashMap<>();
        Map<Long, SerieMensal> estimado = estimar(e, categorias, grupos, realizado, previsto, bases);
        estimado.forEach((id, serie) -> previsto.merge(id, serie, SerieMensal::somar));
        estimado.forEach(itens::estimado);

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
                itens.limpar(c.id());
                for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
                    somarMembro(porMembro, null, TipoMovimento.DESPESA, mes,
                            calcReal.mes(mes).somar(calcPrev.mes(mes)));
                }
            }
        }

        Map<TipoMovimento, List<LinhaCategoria>> linhasPorTipo = linhasCategoria(categorias.values(), grupos,
                realizado, previsto, estimado, bases, itens);
        Dinheiro totalTributavelAno = baseRealizada.somar(basePrevista).total();
        int fechadoAte = ultimoMesFechado(e);
        List<LinhaGrupo> receitas = linhasGrupo(TipoMovimento.RECEITA, grupos, linhasPorTipo, totalTributavelAno,
                fechadoAte);
        List<LinhaGrupo> despesas = linhasGrupo(TipoMovimento.DESPESA, grupos, linhasPorTipo, totalTributavelAno,
                fechadoAte);

        Fluxo fluxo = fluxo(e, receitas, despesas);
        return new VisaoAnual(e.ano(), receitas, despesas, fluxo, Variacao.de(fluxo.despesasTotal(), fechadoAte),
                membros(porMembro));
    }

    /**
     * Estimativa dos próximos meses (só no ano corrente, só despesa que não é recorrente nem calculada, ativa), pela
     * regra da categoria ou da família:
     * - MEDIA_3M: a média dos últimos 3 meses que já passaram, mesmo que do ano anterior (em janeiro, out a dez);
     * - MEDIA, MAXIMO, MINIMO: dos meses que já passaram no ano.
     * Só contam meses a partir do primeiro com algum lançamento da família (antes disso ela ainda não usava o app).
     * Cada mês futuro recebe só o que falta para chegar na estimativa, descontando o que já foi lançado ou está previsto
     * ali (parcelas, recorrências). O mês corrente não é estimado: está em andamento. A estimativa não entra na visão
     * por pessoa. Se a regra da categoria muda a partir de um mês futuro, os meses antes dele seguem a regra de antes.
     * Os trechos de cada categoria (regra, de quem é, desde quando e os meses usados) vão em {@code bases}.
     */
    private static Map<Long, SerieMensal> estimar(Entrada e, Map<Long, Categoria> categorias, Map<Long, Grupo> grupos,
                                                  Map<Long, SerieMensal> realizado, Map<Long, SerieMensal> previsto,
                                                  Map<Long, List<BaseEstimativa>> bases) {
        Map<Long, SerieMensal> estimado = new HashMap<>();
        if (e.hoje() == null || e.hoje().getYear() != e.ano() || e.estimativaFamilia() == null) {
            return estimado;
        }
        Competencia atual = Competencia.de(e.hoje());
        int mesAtual = atual.numeroMes();
        Competencia inicio = Stream.concat(e.anteriores().stream(), e.lancamentos().stream())
                .map(l -> Competencia.de(l.data()))
                .filter(c -> c.antesDe(atual))
                .min(Comparator.naturalOrder()).orElse(null);
        Map<Long, Map<Competencia, Dinheiro>> anoAnterior = new HashMap<>();
        for (Lancamento l : e.anteriores()) {
            anoAnterior.computeIfAbsent(l.categoriaId(), k -> new HashMap<>())
                    .merge(Competencia.de(l.data()), l.valor(), Dinheiro::somar);
        }
        for (Categoria c : categorias.values()) {
            Grupo g = grupos.get(c.grupoId());
            if (g.tipo() != TipoMovimento.DESPESA || c.recorrente() || c.calculada() || !c.ativa() || !g.ativo()) {
                continue;
            }
            SerieMensal real = realizado.getOrDefault(c.id(), SerieMensal.ZERO);
            Map<Competencia, Dinheiro> antes = anoAnterior.getOrDefault(c.id(), Map.of());
            List<BaseEstimativa> trechos = new ArrayList<>();
            for (Competencia desde : Stream.of(atual, c.estimativaDesde())
                    .filter(m -> m != null && !m.antesDe(atual)).distinct().sorted().toList()) {
                Estimativa regra = c.estimativaOu(e.estimativaFamilia(), desde);
                BaseEstimativa.Origem origem = c.estimativaEm(desde) != null ? BaseEstimativa.Origem.CATEGORIA
                        : BaseEstimativa.Origem.FAMILIA;
                if (trechos.isEmpty() || trechos.getLast().regra() != regra || trechos.getLast().origem() != origem) {
                    trechos.add(baseCategoria(e.ano(), atual, inicio, regra, origem, desde, real, antes));
                }
            }
            bases.put(c.id(), trechos);
            SerieMensal prev = previsto.getOrDefault(c.id(), SerieMensal.ZERO);
            SerieMensal serie = SerieMensal.ZERO;
            for (int mes = mesAtual + 1; mes <= SerieMensal.MESES; mes++) {
                BaseEstimativa trecho = BaseEstimativa.em(trechos, Competencia.of(e.ano(), mes));
                if (trecho == null || !trecho.estima()) {
                    continue;
                }
                Dinheiro falta = trecho.valor().subtrair(real.mes(mes)).subtrair(prev.mes(mes));
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

    /** Os meses que a regra usa e o valor estimado (nulo = não estima: NENHUMA, sem meses ou tudo zerado). */
    private static BaseEstimativa baseCategoria(int ano, Competencia atual, Competencia inicio, Estimativa regra,
                                                BaseEstimativa.Origem origem, Competencia desde, SerieMensal real,
                                                Map<Competencia, Dinheiro> antes) {
        List<BaseEstimativa.Mes> meses = new ArrayList<>();
        if (regra != Estimativa.NENHUMA && inicio != null) {
            Competencia primeiro = regra.doAno()
                    ? (inicio.ano() == ano ? inicio : Competencia.of(ano, 1))
                    : atual.mais(-Estimativa.MESES_RECENTES);
            for (Competencia m = primeiro.antesDe(inicio) ? inicio : primeiro; m.antesDe(atual); m = m.seguinte()) {
                Dinheiro valor = m.ano() == ano ? real.mes(m.numeroMes()) : antes.getOrDefault(m, Dinheiro.ZERO);
                meses.add(new BaseEstimativa.Mes(m, valor));
            }
        }
        Dinheiro valor = meses.isEmpty() ? null : regra.aplicar(meses.stream().map(BaseEstimativa.Mes::valor).toList());
        return new BaseEstimativa(regra, origem, meses, valor != null && valor.ehPositivo() ? valor : null, desde);
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
            Map<Long, SerieMensal> previsto, Map<Long, SerieMensal> estimado, Map<Long, List<BaseEstimativa>> bases,
            Itens itens) {
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
                        null, null, itens.da(c.id(), bases.getOrDefault(c.id(), List.of())),
                        bases.getOrDefault(c.id(), List.of())));
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
                        rank, l.itens(), l.previsoes()));
            }
            porTipo.put(tipo, finais);
        }
        return porTipo;
    }

    /**
     * Os itens de cada categoria: a recorrência (o previsto e o que já foi confirmado dela, com o nome dela), cada
     * descrição avulsa (sem diferença de maiúscula, acento ou espaço; vale o nome do primeiro lançamento) e a
     * estimativa dos próximos meses.
     */
    private static final class Itens {

        private static final String ESTIMATIVA = "Estimativa pelos meses anteriores";

        private final Map<Long, String> nomesRecorrencias = new HashMap<>();
        private final Map<Long, List<BaseEstimativa>> previsoesRecorrencias = new HashMap<>();
        private final Map<Long, Map<String, String>> nomes = new HashMap<>();
        private final Map<Long, Map<String, SerieMensal[]>> series = new HashMap<>();

        Itens(List<Recorrencia> recorrencias, PrevisaoRecorrencias previsao) {
            recorrencias.forEach(r -> {
                nomesRecorrencias.put(r.id(), r.descricao());
                previsoesRecorrencias.put(r.id(), previsao.trechos(r));
            });
        }

        void realizado(Long categoriaId, Lancamento l, int mes) {
            String chave = l.recorrenciaId() != null ? "r" + l.recorrenciaId() : "d" + normalizar(l.descricao());
            String nome = l.recorrenciaId() != null
                    ? nomesRecorrencias.getOrDefault(l.recorrenciaId(), l.descricao()) : l.descricao();
            somar(categoriaId, chave, nome, mes, l.valor(), 0);
        }

        void previsto(Long categoriaId, Recorrencia r, int mes, Dinheiro valor) {
            somar(categoriaId, "r" + r.id(), r.descricao(), mes, valor, 1);
        }

        void estimado(Long categoriaId, SerieMensal serie) {
            for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
                if (!serie.mes(mes).ehZero()) {
                    somar(categoriaId, "e", ESTIMATIVA, mes, serie.mes(mes), 1);
                }
            }
        }

        void limpar(Long categoriaId) {
            series.remove(categoriaId);
            nomes.remove(categoriaId);
        }

        /** @param estimativa como a estimativa da categoria é prevista (vai no item da estimativa) */
        List<LinhaItem> da(Long categoriaId, List<BaseEstimativa> estimativa) {
            Map<String, SerieMensal[]> daCategoria = series.getOrDefault(categoriaId, Map.of());
            Map<String, String> nomesDaCategoria = nomes.getOrDefault(categoriaId, Map.of());
            return daCategoria.entrySet().stream()
                    .map(x -> {
                        Long recorrenciaId = recorrencia(x.getKey());
                        boolean ehEstimativa = x.getKey().equals("e");
                        List<BaseEstimativa> previsoes = ehEstimativa ? estimativa : recorrenciaId != null
                                ? previsoesRecorrencias.getOrDefault(recorrenciaId, List.of()) : List.of();
                        return new LinhaItem(nomesDaCategoria.get(x.getKey()), recorrenciaId, ehEstimativa,
                                x.getValue()[0], x.getValue()[1], previsoes);
                    })
                    .sorted(Comparator.comparing((LinhaItem i) -> i.total().total()).reversed()
                            .thenComparing(LinhaItem::descricao, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        private void somar(Long categoriaId, String chave, String nome, int mes, Dinheiro valor, int qual) {
            nomes.computeIfAbsent(categoriaId, k -> new HashMap<>()).putIfAbsent(chave, nome);
            SerieMensal[] s = series.computeIfAbsent(categoriaId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(chave, k -> new SerieMensal[] {SerieMensal.ZERO, SerieMensal.ZERO});
            s[qual] = s[qual].somarNoMes(mes, valor);
        }

        private static Long recorrencia(String chave) {
            return chave.startsWith("r") ? Long.valueOf(chave.substring(1)) : null;
        }

        private static String normalizar(String descricao) {
            return Normalizer.normalize(descricao, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        }
    }

    /** Inflação só de mês fechado: ano passado inteiro, no corrente até o mês anterior, ano futuro nenhum. */
    private static int ultimoMesFechado(Entrada e) {
        if (e.hoje() == null || e.ano() < e.hoje().getYear()) {
            return SerieMensal.MESES;
        }
        return e.ano() > e.hoje().getYear() ? 0 : e.hoje().getMonthValue() - 1;
    }

    private static List<LinhaGrupo> linhasGrupo(TipoMovimento tipo, Map<Long, Grupo> grupos,
                                                Map<TipoMovimento, List<LinhaCategoria>> linhasPorTipo,
                                                Dinheiro totalTributavelAno, int fechadoAte) {
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
                            Variacao.de(total, fechadoAte),
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
        Map<Long, TipoMovimento> tipoGrupo = e.grupos().stream()
                .collect(Collectors.toMap(Grupo::id, Grupo::tipo));
        Map<Long, TipoMovimento> tipoPorCategoria = e.categorias().stream()
                .filter(c -> tipoGrupo.containsKey(c.grupoId()))
                .collect(Collectors.toMap(Categoria::id, c -> tipoGrupo.get(c.grupoId())));
        // Saldo informado de outro dia (ex.: hoje) vira o do dia 1º, desfazendo o lançado de 1º até esse dia.
        Map<Competencia, Dinheiro> saldos = e.saldos().stream()
                .collect(Collectors.toMap(SaldoMes::competencia, s -> s.noDiaPrimeiro(s.dataReferencia() == null
                        ? null : MovimentoDoMes.ate(s.dataReferencia(), e.lancamentos(), tipoPorCategoria))));

        SerieMensal sobra = SerieMensal.ZERO;
        List<Boolean> informada = new ArrayList<>();
        // Janeiro continua de onde dezembro parou, se já havia saldo informado antes deste ano.
        Dinheiro fluxoAnterior = e.sobraAnoAnterior() != null ? e.sobraAnoAnterior() : Dinheiro.ZERO;
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
        SerieMensal sobraDoMes = receitasFixas.somar(receitasVariaveis).subtrair(despesasFixas)
                .subtrair(despesasVariaveis);
        List<MetaAporteMes> metas = META_APORTE.calcular(e.ano(), sobraDoMes, e.aplicacoes(), ultimoMesFechado(e),
                e.metaAporte(), e.metasAporte());
        SerieMensal aportePrevisto = SerieMensal.ZERO;
        for (int mes = 1; mes <= SerieMensal.MESES; mes++) {
            grau.add(Percentual.razao(e.rendaInvestimentos().mes(mes), despesasFixas.mes(mes)));
            Percentual meta = metas.get(mes - 1).meta();
            if (meta != null) {
                aportePrevisto = aportePrevisto.comMes(mes, meta.aplicarSobre(sobraDoMes.mes(mes).maximo(Dinheiro.ZERO)));
            }
        }
        return new Fluxo(sobra, informada, receitasFixas, receitasVariaveis, e.aplicacoes(), despesasFixas,
                despesasVariaveis, e.rendaInvestimentos(), grau, aportePrevisto, e.sobraAnoAnterior() != null, metas);
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
