package br.com.familianca.domain.vo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/** Percentual em % (ex.: 2 = 2%). Resultados de cálculo saem com 2 casas, HALF_UP. */
public record Percentual(BigDecimal valor) {

    public static final Percentual ZERO = new Percentual(BigDecimal.ZERO);

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    public Percentual {
        Objects.requireNonNull(valor, "valor");
        valor = valor.stripTrailingZeros();
        if (valor.scale() < 0) {
            valor = valor.setScale(0);
        }
    }

    public static Percentual of(String valor) {
        return new Percentual(new BigDecimal(valor));
    }

    public static Percentual of(BigDecimal valor) {
        return new Percentual(valor);
    }

    /** parte ÷ todo em %, ou nulo se o todo é zero. */
    public static Percentual razao(Dinheiro parte, Dinheiro todo) {
        if (todo.ehZero()) {
            return null;
        }
        return deFracao(parte.valor().divide(todo.valor(), MathContext.DECIMAL64));
    }

    /** Variação de anterior para atual em %, ou nulo se um dos dois é zero (não há base de comparação). */
    public static Percentual variacao(Dinheiro atual, Dinheiro anterior) {
        if (anterior.ehZero() || atual.ehZero()) {
            return null;
        }
        return deFracao(atual.valor().divide(anterior.valor(), MathContext.DECIMAL64).subtract(BigDecimal.ONE));
    }

    /** 0,05 → 5%, com 2 casas. */
    public static Percentual deFracao(BigDecimal fracao) {
        return new Percentual(fracao.multiply(CEM).setScale(2, RoundingMode.HALF_UP));
    }

    /** 5% → 0,05, sem arredondar. */
    public BigDecimal fracao() {
        return valor.divide(CEM, MathContext.DECIMAL64);
    }

    public Dinheiro aplicarSobre(Dinheiro base) {
        return new Dinheiro(base.valor().multiply(valor).movePointLeft(2));
    }

    public boolean ehNegativo() {
        return valor.signum() < 0;
    }

    public String formatado() {
        return valor.toPlainString().replace('.', ',') + "%";
    }

    @Override
    public String toString() {
        return formatado();
    }
}
