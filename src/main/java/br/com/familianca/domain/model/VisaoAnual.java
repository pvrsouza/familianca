package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import br.com.familianca.domain.vo.SerieMensal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * O ano da família mês a mês: receitas e despesas por grupo e categoria (realizado + previsto), o fluxo de caixa,
 * a inflação familiar e quanto cada pessoa pesa no orçamento. Calculada por
 * {@link br.com.familianca.domain.service.CalculadoraVisaoAnual}.
 */
public record VisaoAnual(int ano, List<LinhaGrupo> receitas, List<LinhaGrupo> despesas, Fluxo fluxo,
                         Variacao inflacaoFamiliar, List<LinhaMembro> membros) {

    /**
     * Até quantos anos depois do atual a previsão alcança (2026 → 2027 e 2028). Mais adiante não há visão do ano nem
     * ocorrência de recorrência para confirmar, pular ou ajustar: projetar tão longe não ajuda e só custa.
     */
    public static final int ANOS_DE_PREVISAO = 2;

    /** Último ano com previsão (e com visão anual). */
    public static int ultimoAnoPrevisto(LocalDate hoje) {
        return hoje.getYear() + ANOS_DE_PREVISAO;
    }

    /**
     * @param realizado     lançamentos (e o cálculo por alíquota sobre receitas realizadas)
     * @param previsto      ocorrências de recorrências ainda não confirmadas nem puladas, mais a estimativa
     * @param estimado      a parte do previsto que vem da estimativa (média, máximo ou mínimo dos meses passados)
     * @param participacao  peso no total do tipo (receitas ou despesas) no ano
     * @param rank          posição entre as categorias do mesmo tipo pelo total do ano (1 = maior); nulo sem valor
     * @param itens         o que compõe a categoria (cada recorrência, cada descrição avulsa, a estimativa), do
     *                      maior para o menor no ano; vazio na categoria calculada por alíquota
     * @param previsoes     como a despesa avulsa da categoria é estimada do mês atual em diante: um trecho por regra
     *                      (regra, de quem é, desde quando e os meses usados); vazio quando não recebe estimativa
     *                      (receita, categoria que se repete ou calculada, ano que não é o atual)
     */
    public record LinhaCategoria(Categoria categoria, SerieMensal realizado, SerieMensal previsto,
                                 SerieMensal estimado, Percentual participacao, Integer rank, List<LinhaItem> itens,
                                 List<BaseEstimativa> previsoes) {

        public LinhaCategoria(Categoria categoria, SerieMensal realizado, SerieMensal previsto, SerieMensal estimado,
                              Percentual participacao, Integer rank, List<LinhaItem> itens) {
            this(categoria, realizado, previsto, estimado, participacao, rank, itens, List.of());
        }

        public LinhaCategoria {
            itens = List.copyOf(itens);
            previsoes = previsoes == null ? List.of() : List.copyOf(previsoes);
        }

        /** A estimativa que vale no mês atual, se a categoria estima algo. */
        public BaseEstimativa baseEstimativa() {
            return previsoes.isEmpty() || !previsoes.getFirst().estima() ? null : previsoes.getFirst();
        }

        public SerieMensal total() {
            return realizado.somar(previsto);
        }
    }

    /**
     * Um item dentro da categoria, para abrir a linha agrupada (ex.: os três telefones de "Telefone e celular").
     * recorrenciaId: o item é essa recorrência (nulo = descrição avulsa ou estimativa); estimativa: é a estimativa.
     * previsoes: como o item é previsto do mês atual em diante (a conta que se repete e a estimativa); vazio nos
     * outros.
     */
    public record LinhaItem(String descricao, Long recorrenciaId, boolean estimativa, SerieMensal realizado,
                            SerieMensal previsto, List<BaseEstimativa> previsoes) {

        public LinhaItem {
            previsoes = previsoes == null ? List.of() : List.copyOf(previsoes);
        }

        public LinhaItem(String descricao, Long recorrenciaId, boolean estimativa, SerieMensal realizado,
                         SerieMensal previsto) {
            this(descricao, recorrenciaId, estimativa, realizado, previsto, List.of());
        }

        public SerieMensal total() {
            return realizado.somar(previsto);
        }
    }

    /** @param pesoSobreTributaveis grupos da empresa: total do ano ÷ receitas tributáveis do ano */
    public record LinhaGrupo(Grupo grupo, List<LinhaCategoria> categorias, SerieMensal realizado,
                             SerieMensal previsto, Percentual participacao, Variacao inflacao,
                             Percentual pesoSobreTributaveis) {

        public SerieMensal total() {
            return realizado.somar(previsto);
        }
    }

    /**
     * Fluxo de caixa no formato da planilha:
     * (1) sobra do mês anterior: saldo informado no mês ou, sem ele, o fluxo líquido do mês anterior;
     * (2) renda total; (3) aplicações (aportes líquidos nos investimentos);
     * (4) saldo disponível = 1 + 2 − 3; (5) despesas; fluxo líquido = 4 − 5.
     *
     * @param sobraInformada    por mês: true se a sobra veio de um saldo informado
     * @param grauIndependencia renda dos investimentos ÷ despesas fixas, por mês (nulo sem despesas fixas)
     * @param aportePrevisto    meta de aporte do mês × (renda − despesas), nunca negativo
     * @param saldoAntesDoAno   true se há saldo informado num ano anterior (janeiro vem da sobra de dezembro)
     * @param metasAporte       a meta de aporte de cada mês (12), com de onde veio
     */
    public record Fluxo(SerieMensal sobraMesAnterior, List<Boolean> sobraInformada, SerieMensal receitasFixas,
                        SerieMensal receitasVariaveis, SerieMensal aplicacoes, SerieMensal despesasFixas,
                        SerieMensal despesasVariaveis, SerieMensal rendaInvestimentos,
                        List<Percentual> grauIndependencia, SerieMensal aportePrevisto, boolean saldoAntesDoAno,
                        List<MetaAporteMes> metasAporte) {

        public Fluxo {
            sobraInformada = List.copyOf(sobraInformada);
            metasAporte = List.copyOf(metasAporte);
            grauIndependencia = Collections.unmodifiableList(new ArrayList<>(grauIndependencia));
        }

        public SerieMensal rendaTotal() {
            return receitasFixas.somar(receitasVariaveis);
        }

        public SerieMensal despesasTotal() {
            return despesasFixas.somar(despesasVariaveis);
        }

        public SerieMensal saldoDisponivel() {
            return sobraMesAnterior.somar(rendaTotal()).subtrair(aplicacoes);
        }

        public SerieMensal fluxoLiquido() {
            return saldoDisponivel().subtrair(despesasTotal());
        }

        /** No ano, a sobra é a de janeiro (o ponto de partida). */
        public Dinheiro sobraAno() {
            return sobraMesAnterior.mes(1);
        }

        public Dinheiro saldoDisponivelAno() {
            return sobraAno().somar(rendaTotal().total()).subtrair(aplicacoes.total());
        }

        public Dinheiro fluxoLiquidoAno() {
            return saldoDisponivelAno().subtrair(despesasTotal().total());
        }

        public Percentual grauIndependenciaAno() {
            return Percentual.razao(rendaInvestimentos.total(), despesasFixas.total());
        }

        /** Como os meses do ano entram na base da meta dos meses seguintes (o ano seguinte começa daqui). */
        public List<BaseMeta> baseDasMetas() {
            return metasAporte.stream().map(MetaAporteMes::naBase).filter(Objects::nonNull).toList();
        }
    }

    /** De onde veio a meta de aporte do mês. */
    public enum OrigemMeta {
        /** Definida à mão para o mês. */
        DEFINIDA,
        /** Estimada pela regra da família a partir dos meses anteriores. */
        ESTIMADA,
        /** A meta de partida da família: a regra não estima ou ainda não tem base. */
        PARTIDA
    }

    /**
     * Um mês na base da meta: o percentual realizado (mês fechado) ou a meta (mês atual ou futuro).
     *
     * @param realizado true = percentual realizado de um mês fechado; false = a meta de um mês que não fechou
     */
    public record BaseMeta(Competencia competencia, Percentual valor, boolean realizado) {
    }

    /**
     * A meta de aporte de um mês: % da sobra (renda − despesas) que a família quer investir.
     *
     * @param meta      nula = sem meta (sem aporte previsto)
     * @param origem    nula quando não há meta
     * @param realizado % da sobra que foi de fato aplicado (só mês fechado com sobra; de 0 a 100); nulo = não se aplica
     * @param base      os meses usados na estimativa (vazia se não foi estimada)
     * @param naBase    como este mês entra na base dos seguintes; nulo = fica de fora
     */
    public record MetaAporteMes(Percentual meta, OrigemMeta origem, Percentual realizado, List<BaseMeta> base,
                                BaseMeta naBase) {

        public MetaAporteMes {
            base = List.copyOf(base);
        }
    }

    /** @param membroId nulo = valores calculados por alíquota (empresa), que não são de uma pessoa */
    public record LinhaMembro(Long membroId, SerieMensal receitas, SerieMensal despesas,
                              Percentual participacaoReceitas, Percentual participacaoDespesas) {
    }
}
