package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Dinheiro;
import br.com.familianca.domain.vo.SerieMensal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VariacaoTest {

    // jan 100, fev 200, mar 100 (fechados), abr 50 (em andamento), mai 300 (previsto).
    private final SerieMensal serie = SerieMensal.ZERO.comMes(1, Dinheiro.of("100")).comMes(2, Dinheiro.of("200"))
            .comMes(3, Dinheiro.of("100")).comMes(4, Dinheiro.of("50")).comMes(5, Dinheiro.of("300"));

    @Test
    void soAteOUltimoMesFechado() {
        Variacao v = Variacao.de(serie, 3);

        assertThat(v.mensal().get(1).valor()).isEqualByComparingTo("100");
        assertThat(v.mensal().get(2).valor()).isEqualByComparingTo("-50");
        assertThat(v.mensal().get(3)).as("abril em andamento").isNull();
        assertThat(v.mensal().get(4)).as("maio previsto").isNull();
        // 2 × 0,5 − 1: o acumulado vai só até março.
        assertThat(v.acumulada().valor()).isEqualByComparingTo("0");
        assertThat(Variacao.de(serie).mensal().get(3).valor()).as("sem limite, como antes").isEqualByComparingTo("-50");
    }

    @Test
    void semMesFechadoNaoHaVariacao() {
        Variacao v = Variacao.de(serie, 0);

        assertThat(v.mensal()).containsOnlyNulls();
        assertThat(v.acumulada()).isNull();
    }
}
