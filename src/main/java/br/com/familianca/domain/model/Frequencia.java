package br.com.familianca.domain.model;

/** De quantos em quantos meses a recorrência acontece, contando a partir do mês de início. */
public enum Frequencia {
    MENSAL(1),
    BIMESTRAL(2),
    TRIMESTRAL(3),
    SEMESTRAL(6),
    ANUAL(12);

    private final int meses;

    Frequencia(int meses) {
        this.meses = meses;
    }

    public int meses() {
        return meses;
    }

    public int vezesPorAno() {
        return 12 / meses;
    }
}
