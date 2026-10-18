package br.com.familianca.domain.exception;

import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.Recurso;

/** O plano em vigor da família não inclui o recurso, ou um limite do plano foi atingido (402). */
public class RecursoForaDoPlanoException extends RuntimeException {

    private final Plano plano;
    private final Recurso recurso;

    public RecursoForaDoPlanoException(Plano plano, Recurso recurso, String mensagem) {
        super(mensagem);
        this.plano = plano;
        this.recurso = recurso;
    }

    public Plano plano() {
        return plano;
    }

    /** Nulo quando é limite (ex.: quantidade de membros), não recurso. */
    public Recurso recurso() {
        return recurso;
    }
}
