package br.com.familianca.domain.exception;

/** Arquivo enviado vazio, ilegível, grande demais ou de formato não suportado (400). */
public class ArquivoInvalidoException extends RuntimeException {

    public ArquivoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
