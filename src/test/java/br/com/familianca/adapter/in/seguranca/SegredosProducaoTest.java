package br.com.familianca.adapter.in.seguranca;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SegredosProducaoTest {

    private static final String CHAVE_FORTE = "9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c";

    @Test
    void apontaValoresAusentesDeDesenvolvimentoOuFracos() {
        assertThat(new SegredosProducao("", "x".repeat(40), "y".repeat(40)).problemas())
                .singleElement().asString().contains("MFA_CHAVE ausente");
        assertThat(new SegredosProducao(SegredosProducao.CHAVE_MFA_DESENVOLVIMENTO, "x".repeat(40), "y".repeat(40))
                .problemas()).singleElement().asString().contains("MFA_CHAVE fraca");
        assertThat(new SegredosProducao(CHAVE_FORTE, "familianca", "").problemas())
                .hasSize(2).anyMatch(p -> p.contains("DB_PASSWORD")).anyMatch(p -> p.contains("FLYWAY_PASSWORD"));
    }

    @Test
    void soAvisaNaoImpedeASubida() {
        assertThatCode(() -> new SegredosProducao("", "", "").afterPropertiesSet()).doesNotThrowAnyException();
        assertThat(new SegredosProducao(CHAVE_FORTE, "x".repeat(40), "y".repeat(40)).problemas()).isEmpty();
    }
}
