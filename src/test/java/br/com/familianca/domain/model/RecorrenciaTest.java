package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Competencia;
import br.com.familianca.domain.vo.Dinheiro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecorrenciaTest {

    @Test
    void trimestralContaAPartirDoInicio() {
        Recorrencia r = recorrencia(Frequencia.TRIMESTRAL, "2025-11", null, Set.of());

        assertThat(r.ocorrenciasNoAno(2026)).extracting(Competencia::toString)
                .containsExactly("2026-02", "2026-05", "2026-08", "2026-11");
        assertThat(r.valorAnualizado()).isEqualTo(Dinheiro.of("400"));
    }

    @Test
    void fimEPuladas() {
        Recorrencia r = recorrencia(Frequencia.MENSAL, "2026-03", "2026-06", Set.of(Competencia.of(2026, 4)));

        assertThat(r.ocorrenciasNoAno(2026)).extracting(Competencia::toString)
                .containsExactly("2026-03", "2026-05", "2026-06");
        assertThat(r.agendadaEm(Competencia.of(2026, 4))).isTrue();
        assertThat(r.ocorreEm(Competencia.of(2026, 4))).isFalse();
        assertThat(r.agendadaEm(Competencia.of(2026, 7))).isFalse();
    }

    @Test
    void dia31NoMesCurto() {
        Recorrencia r = recorrencia(Frequencia.MENSAL, "2026-01", null, Set.of());
        assertThat(r.dataEm(Competencia.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void confirmaSozinhaSoOQueVenceDesdeQueFoiLigadaAteHoje() {
        // Dia 31 (último dia do mês), ligada em 15/03: março vence 31/03, depois de ligada.
        Recorrencia r = new Recorrencia(1L, 1L, 1L, "Conta", Dinheiro.of("100"), 31, Frequencia.MENSAL,
                Competencia.of(2026, 1), null, null, Set.of(Competencia.of(2026, 4)), LocalDate.of(2026, 3, 15));
        LocalDate hoje = LocalDate.of(2026, 5, 10);

        assertThat(r.confirmaSozinha()).isTrue();
        assertThat(r.confirmaSozinhaEm(Competencia.of(2026, 2), hoje)).as("venceu antes de ligar").isFalse();
        assertThat(r.confirmaSozinhaEm(Competencia.of(2026, 3), hoje)).isTrue();
        assertThat(r.confirmaSozinhaEm(Competencia.of(2026, 4), hoje)).as("pulada").isFalse();
        assertThat(r.confirmaSozinhaEm(Competencia.of(2026, 5), hoje)).as("ainda não venceu").isFalse();
        assertThat(recorrencia(Frequencia.MENSAL, "2026-01", null, Set.of())
                .confirmaSozinhaEm(Competencia.of(2026, 3), hoje)).as("desligada").isFalse();
    }

    @Test
    void unicaAconteceSoNoMesDeInicio() {
        Recorrencia r = recorrencia(Frequencia.UNICA, "2026-11", "2027-05", Set.of());

        assertThat(r.fim()).as("o fim vira o próprio início").isEqualTo(Competencia.of(2026, 11));
        assertThat(r.ocorrenciasNoAno(2026)).extracting(Competencia::toString).containsExactly("2026-11");
        assertThat(r.ocorrenciasNoAno(2027)).isEmpty();
        assertThat(r.valorAnualizado()).isEqualTo(Dinheiro.of("100"));
    }

    @Test
    void vencimentoAjustadoSoNaquelaOcorrencia() {
        Competencia nov = Competencia.of(2026, 11);
        Recorrencia r = recorrencia(Frequencia.MENSAL, "2026-01", null, Set.of())
                .comVencimentoAjustado(nov, LocalDate.of(2026, 12, 2));

        assertThat(r.dataEm(nov)).isEqualTo(LocalDate.of(2026, 12, 2));
        assertThat(r.vencimentoEm(nov)).isEqualTo(LocalDate.of(2026, 12, 2));
        assertThat(r.dataEm(Competencia.of(2026, 10))).isEqualTo(LocalDate.of(2026, 10, 31));
        // Voltar ao dia cadastrado (ou nulo) desfaz o ajuste.
        assertThat(r.comVencimentoAjustado(nov, null).vencimentosAjustados()).isEmpty();
        assertThat(r.comVencimentoAjustado(nov, LocalDate.of(2026, 11, 30)).vencimentosAjustados()).isEmpty();
        assertThatThrownBy(() -> r.comVencimentoAjustado(nov, LocalDate.of(2027, 2, 1)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("até 2 meses");
    }

    @Test
    void semVencimentoECartaoNaoTemVencimento() {
        Competencia out = Competencia.of(2026, 10);
        Recorrencia base = recorrencia(Frequencia.MENSAL, "2026-01", null, Set.of());
        Recorrencia semVencimento = comPagamento(base, true, null);
        Recorrencia cartao = comPagamento(base, false, FormaPagamento.CARTAO_CREDITO);
        Recorrencia boleto = comPagamento(base, false, FormaPagamento.BOLETO_PIX);

        assertThat(base.vencimentoEm(out)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(boleto.temVencimento()).isTrue();
        assertThat(semVencimento.temVencimento()).isFalse();
        assertThat(semVencimento.vencimentoEm(out)).isNull();
        assertThat(semVencimento.dataEm(out)).as("o dia ainda posiciona no mês").isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(cartao.vencimentoEm(out)).isNull();
    }

    @Test
    void validacoes() {
        assertThatThrownBy(() -> recorrencia(Frequencia.MENSAL, "2026-05", "2026-04", Set.of()))
                .isInstanceOf(RegraNegocioException.class).hasMessage("O fim não pode ser antes do início");
        assertThatThrownBy(() -> new Recorrencia(null, 1L, 1L, "X", Dinheiro.of("10"), 0, Frequencia.MENSAL,
                Competencia.of(2026, 1), null, null, Set.of(), null)).isInstanceOf(RegraNegocioException.class);
    }

    private static Recorrencia comPagamento(Recorrencia r, boolean semVencimento, FormaPagamento forma) {
        return new Recorrencia(r.id(), r.categoriaId(), r.membroId(), r.descricao(), r.valor(), r.dia(), r.frequencia(),
                r.inicio(), r.fim(), r.observacao(), r.puladas(), null, null, semVencimento, forma, Map.of());
    }

    private static Recorrencia recorrencia(Frequencia f, String inicio, String fim, Set<Competencia> puladas) {
        return new Recorrencia(1L, 1L, 1L, "Conta", Dinheiro.of("100"), 31, f, Competencia.parse(inicio),
                fim != null ? Competencia.parse(fim) : null, null, puladas, null);
    }
}
