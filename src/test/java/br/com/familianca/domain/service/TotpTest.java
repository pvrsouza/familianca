package br.com.familianca.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** Segredo dos vetores de teste da RFC 6238 (SHA-1): "12345678901234567890". */
    private static final String SEGREDO_RFC = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void vetoresDaRfc6238ComSeisDigitos() {
        // A RFC lista 8 dígitos (94287082, 07081804, 14050471, 89005924); com 6, valem os últimos 6.
        assertThat(Totp.codigo(SEGREDO_RFC, Totp.passo(59))).isEqualTo("287082");
        assertThat(Totp.codigo(SEGREDO_RFC, Totp.passo(1111111109))).isEqualTo("081804");
        assertThat(Totp.codigo(SEGREDO_RFC, Totp.passo(1111111111))).isEqualTo("050471");
        assertThat(Totp.codigo(SEGREDO_RFC, Totp.passo(1234567890))).isEqualTo("005924");
    }

    @Test
    void base32IdaEVolta() {
        assertThat(SEGREDO_RFC).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(new String(Totp.deBase32(SEGREDO_RFC), StandardCharsets.US_ASCII)).isEqualTo("12345678901234567890");
        assertThat(Totp.novoSegredo()).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    void aceitaUmPassoDeToleranciaENaoAceitaCodigoJaUsado() {
        long agora = 1_800_000_000L;
        long passo = Totp.passo(agora);
        String atual = Totp.codigo(SEGREDO_RFC, passo);

        assertThat(Totp.verificar(SEGREDO_RFC, atual, agora, null)).hasValue(passo);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo - 1), agora, null)).hasValue(passo - 1);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo + 1), agora, null)).hasValue(passo + 1);
        assertThat(Totp.verificar(SEGREDO_RFC, Totp.codigo(SEGREDO_RFC, passo - 2), agora, null)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_RFC, atual, agora, passo)).as("reuso").isEmpty();
        assertThat(Totp.verificar(SEGREDO_RFC, "12345", agora, null)).isEmpty();
        assertThat(Totp.verificar(SEGREDO_RFC, null, agora, null)).isEmpty();
    }
}
