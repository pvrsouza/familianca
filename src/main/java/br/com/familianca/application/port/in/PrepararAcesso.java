package br.com.familianca.application.port.in;

/** Primeiro acesso: roda no startup, sem usuário logado. */
public interface PrepararAcesso {

    /**
     * Sem nenhum usuário cadastrado e com e-mail e senha informados, cria o ADMIN da plataforma (com uma família
     * própria). Sem e-mail ou senha, não faz nada: as famílias nascem pelo cadastro público.
     */
    void prepararPrimeiroAcesso(String nome, String email, String senha);
}
