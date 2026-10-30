package br.com.familianca.adapter.in.web.dto;

/** Limites de entrada aceitos pela API (mensagens usadas nas anotações de validação). */
public final class Faixas {

    public static final int ANO_MIN = 2000;
    public static final int ANO_MAX = 2100;
    public static final String ANO_FORA = "deve estar entre 2000 e 2100";
    public static final String ORDEM_NEGATIVA = "não pode ser negativa";

    private Faixas() {
    }
}
