package br.com.familianca.domain.model;

/** Com que frequência um gasto acontece, para a simulação de impacto no ano. */
public enum Periodicidade {
    UNICA(1),
    DIARIA(365),
    DIAS_UTEIS(252),
    SEMANAL(52),
    QUINZENAL(24),
    MENSAL(12),
    BIMESTRAL(6),
    TRIMESTRAL(4),
    SEMESTRAL(2),
    ANUAL(1);

    private final int vezesPorAno;

    Periodicidade(int vezesPorAno) {
        this.vezesPorAno = vezesPorAno;
    }

    public int vezesPorAno() {
        return vezesPorAno;
    }
}
