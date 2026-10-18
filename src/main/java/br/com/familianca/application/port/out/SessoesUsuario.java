package br.com.familianca.application.port.out;

/** Sessões abertas no servidor. Papel e família ficam gravados na sessão; mudou o acesso, as sessões caem. */
public interface SessoesUsuario {

    void encerrarTodas(String email);

    /** Encerra todas menos a da requisição atual (ex.: quem trocou a própria senha continua logado). */
    void encerrarOutras(String email);
}
