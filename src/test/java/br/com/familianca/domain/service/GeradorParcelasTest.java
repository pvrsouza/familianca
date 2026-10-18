package br.com.familianca.domain.service;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.service.GeradorParcelas.ParcelaGerada;
import br.com.familianca.domain.vo.Dinheiro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeradorParcelasTest {

    final GeradorParcelas gerador = new GeradorParcelas();

    @Test
    void centavosDaDivisaoVaoParaAPrimeira() {
        List<ParcelaGerada> parcelas = gerador.gerar(Dinheiro.of("100"), 3, LocalDate.of(2026, 1, 31));

        assertThat(parcelas).extracting(ParcelaGerada::valor)
                .containsExactly(Dinheiro.of("33.34"), Dinheiro.of("33.33"), Dinheiro.of("33.33"));
        assertThat(parcelas).extracting(ParcelaGerada::data)
                .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
        assertThat(parcelas).extracting(ParcelaGerada::numero).containsExactly(1, 2, 3);
    }

    @Test
    void limites() {
        assertThatThrownBy(() -> gerador.gerar(Dinheiro.of("100"), 1, LocalDate.now()))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> gerador.gerar(Dinheiro.of("100"), 61, LocalDate.now()))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> gerador.gerar(Dinheiro.of("0.05"), 10, LocalDate.now()))
                .isInstanceOf(RegraNegocioException.class);
    }
}
