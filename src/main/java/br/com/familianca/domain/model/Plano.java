package br.com.familianca.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Planos de assinatura: o que cada um libera e quantas pessoas cabem na família (membros ativos + convites
 * pendentes). Preço e cobrança recorrente ficam com o gateway de pagamento (fase de assinatura).
 */
public enum Plano {
    GRATUITO("Gratuito", 2, EnumSet.noneOf(Recurso.class)),
    FAMILIA("Família", 6, EnumSet.of(Recurso.INVESTIMENTOS)),
    PREMIUM("Premium", 12, EnumSet.allOf(Recurso.class));

    private final String rotulo;
    private final int limiteMembros;
    private final Set<Recurso> recursos;

    Plano(String rotulo, int limiteMembros, Set<Recurso> recursos) {
        this.rotulo = rotulo;
        this.limiteMembros = limiteMembros;
        this.recursos = recursos;
    }

    public boolean permite(Recurso recurso) {
        return recursos.contains(recurso);
    }

    public String rotulo() { return rotulo; }
    public int limiteMembros() { return limiteMembros; }
    public Set<Recurso> recursos() { return Set.copyOf(recursos); }
}
