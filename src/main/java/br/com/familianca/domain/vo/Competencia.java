package br.com.familianca.domain.vo;

import br.com.familianca.domain.exception.RegraNegocioException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record Competencia(YearMonth mes) implements Comparable<Competencia> {

    public Competencia {
        Objects.requireNonNull(mes, "mes");
    }

    public static Competencia of(int ano, int mes) {
        return new Competencia(YearMonth.of(ano, mes));
    }

    public static Competencia de(LocalDate data) {
        return new Competencia(YearMonth.from(data));
    }

    public static Competencia parse(String texto) {
        try {
            return new Competencia(YearMonth.parse(texto));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new RegraNegocioException("Competência inválida: " + texto + ". Use AAAA-MM");
        }
    }

    public LocalDate primeiroDia() {
        return mes.atDay(1);
    }

    public LocalDate ultimoDia() {
        return mes.atEndOfMonth();
    }

    public int diasNoMes() {
        return mes.lengthOfMonth();
    }

    /** Dia do mês limitado ao último dia (ex.: dia 31 em fevereiro vira 28/29). */
    public LocalDate diaLimitado(int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    public Competencia seguinte() {
        return new Competencia(mes.plusMonths(1));
    }

    public Competencia anterior() {
        return new Competencia(mes.minusMonths(1));
    }

    public Competencia mais(int meses) {
        return new Competencia(mes.plusMonths(meses));
    }

    /** Meses de distância até a outra (positivo se a outra é posterior). */
    public long mesesAte(Competencia outra) {
        return ChronoUnit.MONTHS.between(mes, outra.mes);
    }

    public int ano() {
        return mes.getYear();
    }

    /** 1 a 12. */
    public int numeroMes() {
        return mes.getMonthValue();
    }

    public boolean antesDe(Competencia outra) {
        return mes.isBefore(outra.mes);
    }

    public boolean depoisDe(Competencia outra) {
        return mes.isAfter(outra.mes);
    }

    @Override
    public int compareTo(Competencia outra) {
        return mes.compareTo(outra.mes);
    }

    @Override
    public String toString() {
        return mes.toString();
    }
}
