package br.com.familianca.application.port.in;

/** O que o próprio usuário logado faz com a conta dele (qualquer papel). */
public interface GerenciarMinhaConta {

    /**
     * Confere a senha atual e grava a nova (política mínima). As outras sessões do usuário são encerradas; a atual
     * continua.
     */
    void trocarSenha(String senhaAtual, String novaSenha);

    /** Vale a partir do próximo login (o nome também fica na sessão). */
    void renomear(String nome);
}
