package br.com.familianca.adapter.in.web.dto;

import br.com.familianca.application.port.in.ConsultarVisaoAnual.VisaoAnualDetalhada;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.Variacao;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.model.VisaoAnual.Fluxo;
import br.com.familianca.domain.model.VisaoAnual.LinhaCategoria;
import br.com.familianca.domain.model.VisaoAnual.LinhaGrupo;
import br.com.familianca.domain.model.VisaoAnual.LinhaMembro;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.SerieMensal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** A visão anual: tudo em séries de 12 meses (janeiro a dezembro) com o total e a média do ano. */
public record VisaoAnualResponse(int ano, List<String> meses, Resumo resumo, Bloco receitas, Bloco despesas,
                                 List<MembroVisao> membros) {

    public static VisaoAnualResponse de(VisaoAnualDetalhada d) {
        VisaoAnual v = d.visao();
        List<String> meses = IntStream.rangeClosed(1, 12).mapToObj(m -> "%d-%02d".formatted(v.ano(), m)).toList();
        return new VisaoAnualResponse(v.ano(), meses, Resumo.de(v), Bloco.de(v.receitas()), Bloco.de(v.despesas()),
                v.membros().stream().map(m -> MembroVisao.de(m, d.membros())).toList());
    }

    /** valores: 12 meses; ano: total do ano (ou, nas linhas de saldo, o valor do ano como na planilha). */
    public record Serie(List<BigDecimal> valores, BigDecimal ano, BigDecimal mediaMensal) {

        static Serie de(SerieMensal s) {
            return new Serie(Valores.meses(s), s.total().valor(), s.media().valor());
        }

        static Serie de(SerieMensal s, Dinheiro ano) {
            return new Serie(Valores.meses(s), ano.valor(), s.media().valor());
        }
    }

    public record SeriePercentual(List<BigDecimal> valores, BigDecimal ano) {

        static SeriePercentual de(Variacao v) {
            return new SeriePercentual(Valores.percentuais(v.mensal()), Valores.valor(v.acumulada()));
        }
    }

    @Schema(description = "O resumo da planilha: (1) sobra do mês anterior, (2) renda, (3) aplicações, "
            + "(4) saldo disponível = 1 + 2 − 3, (5) despesas, fluxo de caixa líquido = 4 − 5.")
    public record Resumo(
            @Schema(description = "Saldo informado no mês ou, sem ele, o fluxo líquido do mês anterior. No ano: o de janeiro.")
            Serie sobraMesAnterior,
            @Schema(description = "true nos meses em que a sobra veio de um saldo informado.") List<Boolean> sobraInformada,
            Serie rendaTotal, Serie receitasFixas, Serie receitasVariaveis,
            @Schema(description = "Aportes líquidos nos investimentos (zero sem o recurso INVESTIMENTOS).") Serie aplicacoes,
            Serie saldoDisponivel, Serie despesasTotal, Serie despesasFixas, Serie despesasVariaveis,
            Serie fluxoCaixaLiquido, Serie rendaInvestimentos,
            @Schema(description = "% renda dos investimentos ÷ despesas fixas.") SeriePercentual grauIndependencia,
            @Schema(description = "Meta de aporte × (renda − despesas), nunca negativo.") Serie aportePrevisto,
            @Schema(description = "Variação mês a mês das despesas totais e a acumulada no ano (composta).")
            SeriePercentual inflacaoFamiliar) {

        static Resumo de(VisaoAnual v) {
            Fluxo f = v.fluxo();
            return new Resumo(Serie.de(f.sobraMesAnterior(), f.sobraAno()), f.sobraInformada(), Serie.de(f.rendaTotal()),
                    Serie.de(f.receitasFixas()), Serie.de(f.receitasVariaveis()), Serie.de(f.aplicacoes()),
                    Serie.de(f.saldoDisponivel(), f.saldoDisponivelAno()), Serie.de(f.despesasTotal()),
                    Serie.de(f.despesasFixas()), Serie.de(f.despesasVariaveis()),
                    Serie.de(f.fluxoLiquido(), f.fluxoLiquidoAno()), Serie.de(f.rendaInvestimentos()),
                    new SeriePercentual(Valores.percentuais(f.grauIndependencia()), Valores.valor(f.grauIndependenciaAno())),
                    Serie.de(f.aportePrevisto()), SeriePercentual.de(v.inflacaoFamiliar()));
        }
    }

    public record Bloco(Serie total, List<GrupoVisao> grupos) {

        static Bloco de(List<LinhaGrupo> grupos) {
            SerieMensal total = grupos.stream().map(LinhaGrupo::total).reduce(SerieMensal.ZERO, SerieMensal::somar);
            return new Bloco(Serie.de(total), grupos.stream().map(GrupoVisao::de).toList());
        }
    }

    public record GrupoVisao(Long id, String nome, Natureza natureza, boolean empresa, boolean ativo, Serie total,
                             @Schema(description = "Parte lançada.") List<BigDecimal> realizado,
                             @Schema(description = "Parte prevista (recorrências não confirmadas e estimativa).") List<BigDecimal> previsto,
                             @Schema(description = "% do total do tipo no ano.") BigDecimal participacao,
                             @Schema(description = "Inflação do grupo: variação mês a mês e acumulada.") SeriePercentual inflacao,
                             @Schema(description = "Grupos da empresa: % sobre as receitas tributáveis do ano.")
                             BigDecimal pesoSobreTributaveis,
                             List<CategoriaVisao> categorias) {

        static GrupoVisao de(LinhaGrupo l) {
            Grupo g = l.grupo();
            return new GrupoVisao(g.id(), g.nome(), g.natureza(), g.empresa(), g.ativo(), Serie.de(l.total()),
                    Valores.meses(l.realizado()), Valores.meses(l.previsto()), Valores.valor(l.participacao()),
                    SeriePercentual.de(l.inflacao()), Valores.valor(l.pesoSobreTributaveis()),
                    l.categorias().stream().map(CategoriaVisao::de).toList());
        }
    }

    public record CategoriaVisao(Long id, String nome, boolean ativa, boolean calculada, BigDecimal aliquota,
                                 @Schema(description = "Costuma se repetir: ao lançar, já sugere repetir no ano.")
                                 boolean recorrente,
                                 @Schema(description = "Receita que entra na base de cálculo das despesas PJ.")
                                 boolean tributavel,
                                 @Schema(description = "Regra própria de estimativa; nulo = segue a da família.")
                                 Estimativa estimativa,
                                 Serie total, List<BigDecimal> realizado, List<BigDecimal> previsto,
                                 @Schema(description = "Parte do previsto que é estimativa (média, máximo ou mínimo "
                                         + "dos meses que já passaram), para despesas que não se repetem.")
                                 List<BigDecimal> estimado,
                                 @Schema(description = "% do total do tipo no ano.") BigDecimal participacao,
                                 @Schema(description = "Posição entre as categorias do tipo pelo total do ano (1 = maior).")
                                 Integer rank) {

        static CategoriaVisao de(LinhaCategoria l) {
            Categoria c = l.categoria();
            return new CategoriaVisao(c.id(), c.nome(), c.ativa(), c.calculada(), Valores.valor(c.aliquota()),
                    c.recorrente(), c.tributavel(), c.estimativa(), Serie.de(l.total()), Valores.meses(l.realizado()), Valores.meses(l.previsto()),
                    Valores.meses(l.estimado()), Valores.valor(l.participacao()), l.rank());
        }
    }

    @Schema(description = "Quanto cada pessoa pesa no orçamento. membroId nulo = valores calculados por alíquota (empresa).")
    public record MembroVisao(Long membroId, String nome, Serie receitas, Serie despesas,
                              BigDecimal participacaoReceitas, BigDecimal participacaoDespesas) {

        static MembroVisao de(LinhaMembro l, Map<Long, Membro> membros) {
            Membro m = l.membroId() != null ? membros.get(l.membroId()) : null;
            return new MembroVisao(l.membroId(), m != null ? m.nome() : "Calculado (empresa)", Serie.de(l.receitas()),
                    Serie.de(l.despesas()), Valores.valor(l.participacaoReceitas()),
                    Valores.valor(l.participacaoDespesas()));
        }
    }
}
