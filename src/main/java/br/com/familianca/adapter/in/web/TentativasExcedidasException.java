package br.com.familianca.adapter.in.web;

/** Limite de tentativas de login atingido (429). */
public class TentativasExcedidasException extends RuntimeException {

    private final long segundosParaNovaTentativa;

    public TentativasExcedidasException(long segundosParaNovaTentativa) {
        super("Muitas tentativas de login. Tente novamente mais tarde.");
        this.segundosParaNovaTentativa = segundosParaNovaTentativa;
    }

    public long segundosParaNovaTentativa() {
        return segundosParaNovaTentativa;
    }
}
