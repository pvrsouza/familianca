package br.com.familianca.domain.model;

/**
 * De quantos em quantos meses a recorrência acontece, contando a partir do mês de início. UNICA = uma vez só, no mês
 * de início: uma conta avulsa (IPTU em cota única, um boleto de conserto) que fica prevista até ser paga.
 */
public enum Frequencia {
    UNICA(0),
    MENSAL(1),
    BIMESTRAL(2),
    TRIMESTRAL(3),
    SEMESTRAL(6),
    ANUAL(12);

    private final int meses;

    Frequencia(int meses) {
        this.meses = meses;
    }

    /** Intervalo em meses; 0 na UNICA (não se repete). */
    public int meses() {
        return meses;
    }

    /** Vezes num ano cheio; a UNICA conta uma vez. */
    public int vezesPorAno() {
        return this == UNICA ? 1 : 12 / meses;
    }
}
