package br.com.familianca.domain.vo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BinaryOperator;

/** Doze valores, de janeiro (índice 0) a dezembro (11). Imutável. */
public record SerieMensal(List<Dinheiro> meses) {

    public static final int MESES = 12;
    public static final SerieMensal ZERO = new SerieMensal(Collections.nCopies(MESES, Dinheiro.ZERO));

    public SerieMensal {
        if (meses.size() != MESES) {
            throw new IllegalArgumentException("Série mensal precisa de 12 valores");
        }
        meses = List.copyOf(meses);
    }

    /** Valor do mês, de 1 a 12. */
    public Dinheiro mes(int mes) {
        return meses.get(mes - 1);
    }

    public SerieMensal comMes(int mes, Dinheiro valor) {
        List<Dinheiro> novos = new ArrayList<>(meses);
        novos.set(mes - 1, valor);
        return new SerieMensal(novos);
    }

    public SerieMensal somarNoMes(int mes, Dinheiro valor) {
        return comMes(mes, mes(mes).somar(valor));
    }

    public SerieMensal somar(SerieMensal outra) {
        return combinar(outra, Dinheiro::somar);
    }

    public SerieMensal subtrair(SerieMensal outra) {
        return combinar(outra, Dinheiro::subtrair);
    }

    public Dinheiro total() {
        return meses.stream().reduce(Dinheiro.ZERO, Dinheiro::somar);
    }

    /** Média dos 12 meses (o ano inteiro, como na visão anual). */
    public Dinheiro media() {
        return total().proporcional(1, MESES);
    }

    public boolean ehZero() {
        return meses.stream().allMatch(Dinheiro::ehZero);
    }

    private SerieMensal combinar(SerieMensal outra, BinaryOperator<Dinheiro> operacao) {
        List<Dinheiro> novos = new ArrayList<>(MESES);
        for (int i = 0; i < MESES; i++) {
            novos.add(operacao.apply(meses.get(i), outra.meses.get(i)));
        }
        return new SerieMensal(novos);
    }
}
