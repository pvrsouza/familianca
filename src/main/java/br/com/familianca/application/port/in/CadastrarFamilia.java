package br.com.familianca.application.port.in;

import br.com.familianca.domain.model.Membro;

/** Cadastro público (sem login). */
public interface CadastrarFamilia {

    /**
     * Cria o usuário e a família dele, com o plano completo em teste e as categorias padrão; o usuário vira DONO.
     * nomeFamilia em branco = "Família de &lt;primeiro nome&gt;".
     */
    Membro cadastrar(String nome, String email, String senha, String nomeFamilia);
}
