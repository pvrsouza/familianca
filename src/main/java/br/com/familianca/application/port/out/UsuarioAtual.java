package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.PapelFamilia;

/**
 * Usuário autenticado na requisição e a família dele. Os repositórios do orçamento usam {@link #familiaId()} para
 * gravar e filtrar: nenhuma consulta devolve dados de outra família.
 */
public interface UsuarioAtual {

    /** @throws RuntimeException se não há usuário autenticado (nunca devolve nulo). Idem para os demais. */
    Long id();

    Long familiaId();

    Long membroId();

    PapelFamilia papel();

    default boolean dono() {
        return papel() == PapelFamilia.DONO;
    }
}
