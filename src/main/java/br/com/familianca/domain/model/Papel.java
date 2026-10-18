package br.com.familianca.domain.model;

/**
 * Papel na plataforma. ADMIN: operação (assinaturas das famílias, actuator); USER: cliente. O que cada um pode fazer
 * dentro da própria família vem de {@link PapelFamilia}.
 */
public enum Papel {
    ADMIN,
    USER
}
