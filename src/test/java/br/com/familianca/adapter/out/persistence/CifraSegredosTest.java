package br.com.familianca.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CifraSegredosTest {

    @Test
    void semChaveFicaIndisponivelEFalhaFechada() {
        CifraSegredos semChave = new CifraSegredos("");
        assertThat(semChave.disponivel()).isFalse();
        assertThatThrownBy(() -> semChave.decifrar("qualquer")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void comChaveCifraEDecifra() {
        CifraSegredos cifra = new CifraSegredos("9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c");
        String cifrado = cifra.cifrar("SEGREDO");
        assertThat(cifrado).doesNotContain("SEGREDO");
        assertThat(cifra.decifrar(cifrado)).isEqualTo("SEGREDO");
    }
}
