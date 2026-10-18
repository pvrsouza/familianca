package br.com.familianca.domain;

import br.com.familianca.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidacoesTest {

    @Test
    void removeCaracteresDeControleEDeDirecao() {
        assertThat(Validacoes.obrigatorio("  Mer\u0000ca\u001Bdo  ", "nome")).isEqualTo("Mercado");
        // U+202E inverte a exibição do resto do texto.
        assertThat(Validacoes.obrigatorio("Ana‮gnp.exe", "nome")).isEqualTo("Anagnp.exe");
        assertThat(Validacoes.opcional("linha 1\nlinha 2\tfim")).isEqualTo("linha 1\nlinha 2\tfim");
        assertThat(Validacoes.opcional("\u0007\u0008")).isNull();
        assertThatThrownBy(() -> Validacoes.obrigatorio("‏\u0000 ", "nome")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void normalizaAcentosParaNfc() {
        String decomposto = "João"; // "a" + til combinante
        assertThat(Validacoes.obrigatorio(decomposto, "nome")).isEqualTo("João");
    }

    @Test
    void naoEscapaHtml() {
        assertThat(Validacoes.obrigatorio("<b>Pão & leite</b>", "descrição")).isEqualTo("<b>Pão & leite</b>");
    }
}
