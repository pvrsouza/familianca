package br.com.familianca.application.port.in;

/** Usado no login, depois da senha conferida e antes de abrir a sessão. */
public interface VerificarSegundoFator {

    boolean exigido(Long usuarioId);

    /** Código do aplicativo (6 dígitos) ou de recuperação; aceito, fica gasto. */
    boolean confere(Long usuarioId, String codigo);
}
