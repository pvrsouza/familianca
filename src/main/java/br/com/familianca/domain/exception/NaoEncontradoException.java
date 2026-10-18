package br.com.familianca.domain.exception;

public class NaoEncontradoException extends RuntimeException {

    /** Mensagem no masculino: "<recurso> não encontrado: <id>". */
    public NaoEncontradoException(String recurso, Object id) {
        super(recurso + " não encontrado: " + id);
    }

    /** Mensagem livre (ex.: recursos femininos — "Parcela não encontrada: 10"). */
    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
