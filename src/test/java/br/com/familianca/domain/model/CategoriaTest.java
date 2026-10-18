package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.vo.Percentual;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoriaTest {

    static final Grupo RECEITA = new Grupo(1L, TipoMovimento.RECEITA, Natureza.FIXA, "Receitas", false, 1, true);
    static final Grupo DESPESA = new Grupo(2L, TipoMovimento.DESPESA, Natureza.FIXA, "Despesas", false, 1, true);

    @Test
    void tributavelSoEmReceitaEAliquotaSoEmDespesa() {
        assertThatThrownBy(() -> new Categoria(null, 2L, "X", 1, true, true, null).validarNo(DESPESA))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Categoria(null, 1L, "X", 1, true, false, Percentual.of("5")).validarNo(RECEITA))
                .isInstanceOf(RegraNegocioException.class);
        assertThatCode(() -> new Categoria(null, 2L, "ISS", 1, true, false, Percentual.of("5")).validarNo(DESPESA))
                .doesNotThrowAnyException();
    }

    @Test
    void naoRecebeLancamentoDesativadaOuCalculada() {
        assertThatThrownBy(() -> new Categoria(1L, 2L, "X", 1, false, false, null).exigirLancavel(DESPESA))
                .hasMessage("A categoria X está desativada");
        Grupo inativo = new Grupo(2L, TipoMovimento.DESPESA, Natureza.FIXA, "D", false, 1, false);
        assertThatThrownBy(() -> new Categoria(1L, 2L, "X", 1, true, false, null).exigirLancavel(inativo))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Categoria(1L, 2L, "ISS", 1, true, false, Percentual.of("5")).exigirLancavel(DESPESA))
                .hasMessageContaining("calculada");
    }

    @Test
    void calculadaNaoERecorrente() {
        assertThatThrownBy(() -> new Categoria(null, 2L, "ISS", 1, true, false, Percentual.of("5"), true))
                .hasMessageContaining("não pode ser recorrente");
    }

    @Test
    void grupoDaEmpresaSoDeDespesa() {
        assertThatThrownBy(() -> new Grupo(null, TipoMovimento.RECEITA, Natureza.FIXA, "PJ", true, 1, true))
                .isInstanceOf(RegraNegocioException.class);
    }
}
