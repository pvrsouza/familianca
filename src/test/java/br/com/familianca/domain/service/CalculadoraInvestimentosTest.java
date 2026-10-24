package br.com.familianca.domain.service;

import br.com.familianca.domain.model.CarteiraAnual;
import br.com.familianca.domain.model.Investimento;
import br.com.familianca.domain.model.MovimentoInvestimento;
import br.com.familianca.domain.model.TipoInvestimento;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.Percentual;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraInvestimentosTest {

    static final Investimento CDB = new Investimento(1L, "CDB", TipoInvestimento.RENDA_FIXA, true);
    static final Investimento VAZIO_INATIVO = new Investimento(2L, "Antigo", TipoInvestimento.OUTRO, false);

    @Test
    void saldoMesAMesComRentabilidadeAporteESaldoDoExtrato() {
        CarteiraAnual c = new CalculadoraInvestimentos().calcular(2026, List.of(CDB, VAZIO_INATIVO), List.of(
                movimento("2025-12", "10000", null, null),
                movimento("2026-01", null, "1", null),
                movimento("2026-02", "1000", "1", null),
                movimento("2026-03", null, null, "11500"),
                // Depois do ano pedido: ignorado.
                movimento("2027-01", "5000", null, null)));

        assertThat(c.investimentos()).hasSize(1);
        assertThat(c.saldoInicial()).isEqualTo(Dinheiro.of("10000"));
        assertThat(c.saldos().mes(1)).isEqualTo(Dinheiro.of("10100"));
        assertThat(c.rendimentos().mes(2)).isEqualTo(Dinheiro.of("101"));
        assertThat(c.saldos().mes(2)).isEqualTo(Dinheiro.of("11201"));
        // Saldo do extrato prevalece; o rendimento é a diferença.
        assertThat(c.saldos().mes(3)).isEqualTo(Dinheiro.of("11500"));
        assertThat(c.rendimentos().mes(3)).isEqualTo(Dinheiro.of("299"));
        assertThat(c.saldos().mes(12)).isEqualTo(Dinheiro.of("11500"));
        assertThat(c.aportes().total()).isEqualTo(Dinheiro.of("1000"));
        assertThat(c.rendimentos().total()).isEqualTo(Dinheiro.of("500"));
        assertThat(c.rentabilidadeMensal().get(0).valor()).isEqualByComparingTo("1");
        assertThat(c.rentabilidadeMensal().get(2).valor()).isEqualByComparingTo("2.67");
        // 1,01 × 1,01 × (1 + 299/11201) − 1
        assertThat(c.rentabilidadeAno().valor()).isEqualByComparingTo("4.73");
    }

    @Test
    void primeiroSaldoInformadoEhAberturaNaoRendimento() {
        // Começou a acompanhar em out/26 um CDB que já tinha 50 mil; em out ainda aportou 1 mil.
        CarteiraAnual c = new CalculadoraInvestimentos().calcular(2026, List.of(CDB), List.of(
                movimento("2026-10", "1000", null, "51000"),
                movimento("2026-11", null, "1", null),
                movimento("2026-12", null, null, "53000")));

        var cdb = c.investimentos().get(0);
        assertThat(cdb.abertura().competencia()).isEqualTo(Competencia.of(2026, 10));
        assertThat(cdb.abertura().valor()).isEqualTo(Dinheiro.of("50000"));
        assertThat(c.rendimentos().mes(10)).isEqualTo(Dinheiro.ZERO);
        assertThat(c.aportes().mes(10)).isEqualTo(Dinheiro.of("1000"));
        assertThat(c.saldos().mes(10)).isEqualTo(Dinheiro.of("51000"));
        assertThat(c.rendimentos().mes(11)).isEqualTo(Dinheiro.of("510"));
        // Depois da abertura, o saldo do extrato volta a valer como sempre: a diferença é rendimento.
        assertThat(c.rendimentos().mes(12)).isEqualTo(Dinheiro.of("1490"));
        assertThat(c.rendimentos().total()).isEqualTo(Dinheiro.of("2000"));

        // No ano seguinte, a abertura já está no saldo inicial.
        CarteiraAnual depois = new CalculadoraInvestimentos().calcular(2027, List.of(CDB), List.of(
                movimento("2026-10", "1000", null, "51000"), movimento("2026-12", null, null, "53000")));
        assertThat(depois.saldoInicial()).isEqualTo(Dinheiro.of("53000"));
        assertThat(depois.rendimentos().total()).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    void semMovimentosNaoHaRentabilidade() {
        CarteiraAnual c = new CalculadoraInvestimentos().calcular(2026, List.of(CDB), List.of());
        assertThat(c.saldos().ehZero()).isTrue();
        assertThat(c.rentabilidadeMensal()).containsOnlyNulls();
        assertThat(c.rentabilidadeAno()).isNull();
    }

    private static MovimentoInvestimento movimento(String competencia, String aporte, String rentabilidade,
                                                   String saldo) {
        return new MovimentoInvestimento(CDB.id(), Competencia.parse(competencia),
                aporte != null ? Dinheiro.of(aporte) : null, rentabilidade != null ? Percentual.of(rentabilidade) : null,
                saldo != null ? Dinheiro.of(saldo) : null);
    }
}
