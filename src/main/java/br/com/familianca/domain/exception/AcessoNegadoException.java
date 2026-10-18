package br.com.familianca.domain.exception;

/** O usuário está na família mas o papel dele não permite a operação (403). */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
