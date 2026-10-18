package br.com.familianca.domain.service;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Periodicidade;
import br.com.familianca.domain.service.CalculadoraImpacto.Impacto;
import br.com.familianca.domain.vo.Dinheiro;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraImpactoTest {

    final CalculadoraImpacto calculadora = new CalculadoraImpacto();

    @Test
    void cafezinhoDeDiaUtilNoAno() {
        Impacto i = calculadora.calcular(Dinheiro.of("15"), Periodicidade.DIAS_UTEIS, Dinheiro.of("90000"),
                Dinheiro.of("60000"));

        assertThat(i.valorAnual()).isEqualTo(Dinheiro.of("3780"));
        assertThat(i.valorEm5Anos()).isEqualTo(Dinheiro.of("18900"));
        assertThat(i.valorEm10Anos()).isEqualTo(Dinheiro.of("37800"));
        assertThat(i.sobreRendaAnual().valor()).isEqualByComparingTo("4.2");
        assertThat(i.sobreDespesasAnuais().valor()).isEqualByComparingTo("6.3");
        assertThat(i.diasDeRenda()).isEqualByComparingTo("15.3");
    }

    @Test
    void gastoUnicoNaoSeMultiplicaNosAnos() {
        Impacto i = calculadora.calcular(Dinheiro.of("1200"), Periodicidade.UNICA, Dinheiro.ZERO, Dinheiro.ZERO);

        assertThat(i.valorAnual()).isEqualTo(Dinheiro.of("1200"));
        assertThat(i.valorEm10Anos()).isEqualTo(Dinheiro.of("1200"));
        assertThat(i.sobreRendaAnual()).isNull();
        assertThat(i.diasDeRenda()).isNull();
    }

    @Test
    void valorPrecisaSerPositivo() {
        assertThatThrownBy(() -> calculadora.calcular(Dinheiro.ZERO, Periodicidade.MENSAL, Dinheiro.ZERO, Dinheiro.ZERO))
                .isInstanceOf(RegraNegocioException.class);
    }
}
