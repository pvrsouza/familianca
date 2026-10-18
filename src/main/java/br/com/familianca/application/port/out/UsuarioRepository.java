package br.com.familianca.application.port.out;

import br.com.familianca.domain.model.Usuario;

import java.util.Optional;

public interface UsuarioRepository {

    Usuario salvar(Usuario usuario);

    Optional<Usuario> buscarPorId(Long id);

    /** email já normalizado (minúsculas). */
    Optional<Usuario> buscarPorEmail(String email);

    boolean existeAlgum();
}
