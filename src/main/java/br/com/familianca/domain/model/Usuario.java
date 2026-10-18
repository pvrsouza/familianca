package br.com.familianca.domain.model;

import br.com.familianca.domain.exception.RegraNegocioException;

import java.util.Locale;

import static br.com.familianca.domain.Validacoes.obrigatorio;

/**
 * Quem acessa a plataforma. O e-mail é o login (sempre em minúsculas) e não muda depois do cadastro.
 * senhaHash é o hash já calculado (BCrypt); a senha em texto nunca chega ao domínio.
 */
public class Usuario {

    public static final int SENHA_MINIMO = 12;
    /** BCrypt considera só os primeiros 72 bytes; 64 caracteres cabem com folga para acentos comuns. */
    public static final int SENHA_MAXIMO = 64;

    private final Long id;
    private String nome;
    private final String email;
    private String senhaHash;
    private final Papel papel;
    private boolean ativo;

    private Usuario(Long id, String nome, String email, String senhaHash, Papel papel, boolean ativo) {
        this.id = id;
        this.nome = obrigatorio(nome, "nome");
        this.email = normalizarEmail(obrigatorio(email, "email"));
        validarEmail(this.email);
        this.senhaHash = obrigatorio(senhaHash, "senha");
        this.papel = obrigatorio(papel, "papel");
        this.ativo = ativo;
    }

    public static Usuario novo(String nome, String email, String senhaHash, Papel papel) {
        return new Usuario(null, nome, email, senhaHash, papel, true);
    }

    public static Usuario reconstituir(Long id, String nome, String email, String senhaHash, Papel papel, boolean ativo) {
        return new Usuario(id, nome, email, senhaHash, papel, ativo);
    }

    public static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** E-mail já normalizado. */
    public static void validarEmail(String email) {
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new RegraNegocioException("Email inválido");
        }
    }

    /** Política mínima: de 12 a 64 caracteres. Validar antes de calcular o hash. */
    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMO) {
            throw new RegraNegocioException("A senha precisa ter pelo menos " + SENHA_MINIMO + " caracteres");
        }
        if (senha.length() > SENHA_MAXIMO) {
            throw new RegraNegocioException("A senha pode ter no máximo " + SENHA_MAXIMO + " caracteres");
        }
    }

    public void renomear(String nome) {
        this.nome = obrigatorio(nome, "nome");
    }

    public void trocarSenha(String senhaHash) {
        this.senhaHash = obrigatorio(senhaHash, "senha");
    }

    public void inativar() {
        this.ativo = false;
    }

    public boolean admin() {
        return papel == Papel.ADMIN;
    }

    public Long id() { return id; }
    public String nome() { return nome; }
    public String email() { return email; }
    public String senhaHash() { return senhaHash; }
    public Papel papel() { return papel; }
    public boolean ativo() { return ativo; }
}
