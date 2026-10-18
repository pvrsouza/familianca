package br.com.familianca.adapter.in.web;

/** Senha certa, mas a conta tem verificação em duas etapas e o login veio sem o código (401 com mfaObrigatorio). */
public class MfaObrigatorioException extends RuntimeException {

    public MfaObrigatorioException() {
        super("Informe o código do aplicativo autenticador.");
    }
}
