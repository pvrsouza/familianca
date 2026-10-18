package br.com.familianca.domain.model;

/**
 * Como estimar os próximos meses de uma despesa que não se repete (supermercado, farmácia, combustível), a partir
 * dos meses que já passaram no ano. A família escolhe a regra padrão; cada categoria pode seguir o padrão ou ter a sua.
 */
public enum Estimativa {
    /** Não estima: os próximos meses só mostram o que foi lançado ou está previsto por recorrência. */
    NENHUMA,
    /** Média dos meses que já passaram. */
    MEDIA,
    /** O maior mês do ano até aqui (mais conservador). */
    MAXIMO,
    /** O menor mês do ano até aqui. */
    MINIMO
}
