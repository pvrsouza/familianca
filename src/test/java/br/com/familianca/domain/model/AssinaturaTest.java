package br.com.familianca.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaTest {

    static final LocalDate HOJE = LocalDate.of(2026, 10, 17);

    @Test
    void testeValeAteAValidadeEDepoisVoltaAoGratuito() {
        Assinatura teste = Assinatura.teste(HOJE, 30);

        assertThat(teste.planoEmVigor(HOJE.plusDays(30))).isEqualTo(Plano.PREMIUM);
        assertThat(teste.planoEmVigor(HOJE.plusDays(31))).isEqualTo(Plano.GRATUITO);
    }

    @Test
    void canceladaVaiParaOGratuitoEAtivaSemValidadeNaoVence() {
        assertThat(new Assinatura(Plano.FAMILIA, StatusAssinatura.CANCELADA, null).planoEmVigor(HOJE))
                .isEqualTo(Plano.GRATUITO);
        assertThat(new Assinatura(Plano.FAMILIA, StatusAssinatura.ATIVA, null).planoEmVigor(HOJE.plusYears(5)))
                .isEqualTo(Plano.FAMILIA);
    }

    @Test
    void recursosPorPlano() {
        assertThat(Plano.GRATUITO.permite(Recurso.INVESTIMENTOS)).isFalse();
        assertThat(Plano.FAMILIA.permite(Recurso.INVESTIMENTOS)).isTrue();
        assertThat(Plano.FAMILIA.permite(Recurso.DESPESAS_PJ)).isFalse();
        assertThat(Plano.PREMIUM.permite(Recurso.DESPESAS_PJ)).isTrue();
    }
}
