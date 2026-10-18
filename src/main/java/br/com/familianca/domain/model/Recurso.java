package br.com.familianca.domain.model;

/** Funcionalidade que depende do plano. O núcleo (orçamento, visão anual, membros) vale em qualquer plano. */
public enum Recurso {
    INVESTIMENTOS("Investimentos"),
    DESPESAS_PJ("Despesas da empresa (PJ) com alíquotas");

    private final String rotulo;

    Recurso(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
