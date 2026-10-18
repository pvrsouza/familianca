package br.com.familianca.domain.vo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public record Dinheiro(BigDecimal valor) implements Comparable<Dinheiro> {

    public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);

    public Dinheiro {
        Objects.requireNonNull(valor, "valor");
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static Dinheiro of(String valor) {
        return new Dinheiro(new BigDecimal(valor));
    }

    public static Dinheiro of(BigDecimal valor) {
        return new Dinheiro(valor);
    }

    public Dinheiro somar(Dinheiro outro) {
        return new Dinheiro(valor.add(outro.valor));
    }

    public Dinheiro subtrair(Dinheiro outro) {
        return new Dinheiro(valor.subtract(outro.valor));
    }

    public Dinheiro multiplicar(long vezes) {
        return new Dinheiro(valor.multiply(BigDecimal.valueOf(vezes)));
    }

    /** valor × numerador ÷ denominador, arredondado uma única vez. */
    public Dinheiro proporcional(long numerador, long denominador) {
        return new Dinheiro(valor.multiply(BigDecimal.valueOf(numerador))
                .divide(BigDecimal.valueOf(denominador), 2, RoundingMode.HALF_UP));
    }

    public Dinheiro maximo(Dinheiro outro) {
        return compareTo(outro) >= 0 ? this : outro;
    }

    public boolean ehZero() {
        return valor.signum() == 0;
    }

    public boolean ehPositivo() {
        return valor.signum() > 0;
    }

    public boolean ehNegativo() {
        return valor.signum() < 0;
    }

    public boolean maiorQue(Dinheiro outro) {
        return compareTo(outro) > 0;
    }

    public boolean menorQue(Dinheiro outro) {
        return compareTo(outro) < 0;
    }

    @Override
    public int compareTo(Dinheiro outro) {
        return valor.compareTo(outro.valor);
    }

    public String formatado() {
        DecimalFormatSymbols simbolos = DecimalFormatSymbols.getInstance(Locale.ROOT);
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        return "R$ " + new DecimalFormat("#,##0.00", simbolos).format(valor);
    }

    @Override
    public String toString() {
        return formatado();
    }
}
