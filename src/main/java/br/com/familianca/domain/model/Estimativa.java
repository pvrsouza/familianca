package br.com.familianca.domain.model;

import br.com.familianca.domain.vo.Dinheiro;

import java.util.Comparator;
import java.util.List;

/**
 * Como estimar os próximos meses de uma despesa (supermercado, farmácia, combustível, e a conta que se repete com
 * valor variável), a partir do que já aconteceu. A família escolhe a regra padrão; cada categoria pode seguir o padrão
 * ou ter a sua, e cada conta que se repete pode seguir a categoria ou ter a sua.
 */
public enum Estimativa {
    /** Não estima: os próximos meses só mostram o que foi lançado ou está previsto por recorrência. */
    NENHUMA,
    /**
     * Média dos últimos 3 meses que já passaram (na conta que se repete, das 3 últimas confirmações), mesmo que sejam
     * do ano anterior. Acompanha reajustes e funciona em janeiro. Padrão da família.
     */
    MEDIA_3M,
    /** Média dos meses que já passaram no ano. */
    MEDIA,
    /** O maior mês do ano até aqui (mais conservador). */
    MAXIMO,
    /** O menor mês do ano até aqui. */
    MINIMO;

    /** Quantos meses (ou confirmações) a média recente usa. */
    public static final int MESES_RECENTES = 3;

    /** Usa só os meses do ano corrente (MEDIA, MAXIMO, MINIMO); MEDIA_3M olha os últimos meses, de qualquer ano. */
    public boolean doAno() {
        return this == MEDIA || this == MAXIMO || this == MINIMO;
    }

    /** O valor estimado a partir dos valores da base (vazia = zero). */
    public Dinheiro aplicar(List<Dinheiro> valores) {
        if (valores.isEmpty()) {
            return Dinheiro.ZERO;
        }
        return switch (this) {
            case MEDIA, MEDIA_3M -> valores.stream().reduce(Dinheiro.ZERO, Dinheiro::somar).proporcional(1, valores.size());
            case MAXIMO -> valores.stream().max(Comparator.naturalOrder()).orElseThrow();
            case MINIMO -> valores.stream().min(Comparator.naturalOrder()).orElseThrow();
            case NENHUMA -> Dinheiro.ZERO;
        };
    }
}
