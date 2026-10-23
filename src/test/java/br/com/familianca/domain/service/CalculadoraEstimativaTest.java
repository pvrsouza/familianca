package br.com.familianca.domain.service;

import br.com.familianca.domain.model.BaseEstimativa;
import br.com.familianca.domain.model.Categoria;
import br.com.familianca.domain.model.Estimativa;
import br.com.familianca.domain.model.Grupo;
import br.com.familianca.domain.model.Lancamento;
import br.com.familianca.domain.model.Natureza;
import br.com.familianca.domain.model.TipoMovimento;
import br.com.familianca.domain.model.VisaoAnual;
import br.com.familianca.domain.model.VisaoAnual.LinhaCategoria;
import br.com.familianca.domain.service.CalculadoraVisaoAnual.Entrada;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.SerieMensal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estimativa dos próximos meses. Hoje = 10 de maio de 2026; a família começou a lançar em março (fevereiro não
 * conta). Supermercado: março 1.000, abril 1.400, maio (em andamento) 300 e uma parcela de 200 já lançada em junho.
 */
class CalculadoraEstimativaTest {

    static final LocalDate HOJE = LocalDate.of(2026, 5, 10);

    static final Grupo RECEITAS = new Grupo(1L, TipoMovimento.RECEITA, Natureza.FIXA, "Receitas", false, 1, true);
    static final Grupo CASA = new Grupo(2L, TipoMovimento.DESPESA, Natureza.FIXA, "Casa", false, 1, true);

    static final Categoria SALARIO = new Categoria(10L, 1L, "Salário", 1, true, false, null);
    static final Categoria SUPERMERCADO = new Categoria(20L, 2L, "Supermercado", 1, true, false, null);
    static final Categoria ALUGUEL = new Categoria(21L, 2L, "Aluguel", 2, true, false, null, true);
    static final Categoria FARMACIA_MAXIMO = new Categoria(22L, 2L, "Farmácia", 3, true, false, null, false,
            Estimativa.MAXIMO);
    static final Categoria VIAGENS_SEM = new Categoria(23L, 2L, "Viagens", 4, true, false, null, false,
            Estimativa.NENHUMA);

    static final List<Lancamento> LANCAMENTOS = List.of(
            lancamento(1L, SALARIO, "5000", LocalDate.of(2026, 3, 5)),
            lancamento(2L, SALARIO, "5000", LocalDate.of(2026, 4, 5)),
            lancamento(3L, SUPERMERCADO, "1000", LocalDate.of(2026, 3, 15)),
            lancamento(4L, SUPERMERCADO, "1400", LocalDate.of(2026, 4, 15)),
            lancamento(5L, SUPERMERCADO, "300", LocalDate.of(2026, 5, 3)),
            lancamento(6L, SUPERMERCADO, "200", LocalDate.of(2026, 6, 20)),
            lancamento(7L, ALUGUEL, "2000", LocalDate.of(2026, 3, 1)),
            lancamento(8L, ALUGUEL, "2000", LocalDate.of(2026, 4, 1)),
            lancamento(9L, FARMACIA_MAXIMO, "100", LocalDate.of(2026, 3, 9)),
            lancamento(10L, FARMACIA_MAXIMO, "250", LocalDate.of(2026, 4, 9)),
            lancamento(11L, VIAGENS_SEM, "3000", LocalDate.of(2026, 4, 20)));

    @Test
    void mediaDosMesesPassadosNosProximosMeses() {
        LinhaCategoria l = linha(calcular(Estimativa.MEDIA, HOJE), SUPERMERCADO);
        // Média de março e abril = 1.200. Maio está em andamento: não estima.
        assertThat(l.estimado().mes(5)).isEqualTo(Dinheiro.ZERO);
        // Junho já tem 200 lançados: estima só o que falta.
        assertThat(l.estimado().mes(6)).isEqualTo(Dinheiro.of("1000.00"));
        assertThat(l.estimado().mes(7)).isEqualTo(Dinheiro.of("1200.00"));
        assertThat(l.estimado().mes(12)).isEqualTo(Dinheiro.of("1200.00"));
        // A estimativa é parte do previsto e entra no total do ano: 2.900 lançados + 1.000 + 6 × 1.200.
        assertThat(l.previsto().mes(7)).isEqualTo(Dinheiro.of("1200.00"));
        assertThat(l.total().total()).isEqualTo(Dinheiro.of("11100.00"));
    }

    @Test
    void maximoEMinimoDaFamilia() {
        assertThat(linha(calcular(Estimativa.MAXIMO, HOJE), SUPERMERCADO).estimado().mes(8))
                .isEqualTo(Dinheiro.of("1400"));
        assertThat(linha(calcular(Estimativa.MINIMO, HOJE), SUPERMERCADO).estimado().mes(8))
                .isEqualTo(Dinheiro.of("1000"));
    }

    @Test
    void categoriaComRegraPropriaRecorrenteEReceitaNaoSeguemAFamilia() {
        VisaoAnual visao = calcular(Estimativa.MEDIA, HOJE);
        assertThat(linha(visao, FARMACIA_MAXIMO).estimado().mes(9)).isEqualTo(Dinheiro.of("250"));
        assertThat(linha(visao, VIAGENS_SEM).estimado()).isEqualTo(SerieMensal.ZERO);
        assertThat(linha(visao, ALUGUEL).estimado()).isEqualTo(SerieMensal.ZERO);
        assertThat(visao.receitas().getFirst().categorias().getFirst().estimado()).isEqualTo(SerieMensal.ZERO);
        // A estimativa não é de ninguém: fica fora da visão por pessoa.
        assertThat(visao.membros().getFirst().despesas().mes(9)).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    void semEstimativaNaFamiliaEmOutroAnoOuSemHistorico() {
        assertThat(linha(calcular(Estimativa.NENHUMA, HOJE), SUPERMERCADO).estimado()).isEqualTo(SerieMensal.ZERO);
        assertThat(linha(calcular(Estimativa.MEDIA, LocalDate.of(2027, 2, 1)), SUPERMERCADO).estimado())
                .isEqualTo(SerieMensal.ZERO);
        assertThat(linha(calcular(Estimativa.MEDIA, LocalDate.of(2026, 3, 20)), SUPERMERCADO).estimado())
                .isEqualTo(SerieMensal.ZERO);
    }

    @Test
    void mediaDosUltimos3MesesComABaseNaTela() {
        LinhaCategoria l = linha(calcular(Estimativa.MEDIA_3M, HOJE), SUPERMERCADO);
        // Fevereiro, março e abril; fevereiro é antes de a família começar: média de março e abril = 1.200.
        assertThat(l.estimado().mes(7)).isEqualTo(Dinheiro.of("1200.00"));
        assertThat(l.baseEstimativa().regra()).isEqualTo(Estimativa.MEDIA_3M);
        assertThat(l.baseEstimativa().origem()).isEqualTo(BaseEstimativa.Origem.FAMILIA);
        assertThat(l.baseEstimativa().meses()).extracting(m -> m.competencia().toString())
                .containsExactly("2026-03", "2026-04");
        assertThat(linha(calcular(Estimativa.MEDIA_3M, HOJE), FARMACIA_MAXIMO).baseEstimativa().origem())
                .isEqualTo(BaseEstimativa.Origem.CATEGORIA);
    }

    @Test
    void emJaneiroAMediaRecenteUsaOAnoAnterior() {
        List<Lancamento> anteriores = List.of(
                lancamento(30L, SUPERMERCADO, "900", LocalDate.of(2026, 10, 10)),
                lancamento(31L, SUPERMERCADO, "1100", LocalDate.of(2026, 11, 10)),
                lancamento(32L, SUPERMERCADO, "1300", LocalDate.of(2026, 12, 10)));
        VisaoAnual visao = new CalculadoraVisaoAnual().calcular(new Entrada(2027, List.of(RECEITAS, CASA),
                List.of(SALARIO, SUPERMERCADO), List.of(), List.of(), Set.of(), List.of(), SerieMensal.ZERO,
                SerieMensal.ZERO, null, Estimativa.MEDIA_3M, LocalDate.of(2027, 1, 10), PrevisaoRecorrencias.nenhuma(),
                null, anteriores));
        LinhaCategoria l = linha(visao, SUPERMERCADO);
        assertThat(l.estimado().mes(1)).isEqualTo(Dinheiro.ZERO);
        assertThat(l.estimado().mes(2)).isEqualTo(Dinheiro.of("1100.00"));
        assertThat(l.baseEstimativa().meses()).extracting(m -> m.competencia().toString())
                .containsExactly("2026-10", "2026-11", "2026-12");
    }

    private static VisaoAnual calcular(Estimativa daFamilia, LocalDate hoje) {
        return new CalculadoraVisaoAnual().calcular(new Entrada(2026, List.of(RECEITAS, CASA),
                List.of(SALARIO, SUPERMERCADO, ALUGUEL, FARMACIA_MAXIMO, VIAGENS_SEM), LANCAMENTOS, List.of(), Set.of(),
                List.of(), SerieMensal.ZERO, SerieMensal.ZERO, null, daFamilia, hoje));
    }

    private static Lancamento lancamento(Long id, Categoria c, String valor, LocalDate data) {
        return new Lancamento(id, c.id(), 100L, c.nome(), Dinheiro.of(valor), data, null, null, null, null);
    }

    private static LinhaCategoria linha(VisaoAnual visao, Categoria c) {
        return visao.despesas().stream().flatMap(g -> g.categorias().stream())
                .filter(l -> l.categoria().id().equals(c.id())).findFirst().orElseThrow();
    }
}
