package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.model.Papel;
import br.com.familianca.domain.model.Usuario;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@Transactional
public class UsuarioPersistenceAdapter implements UsuarioRepository {

    private final UsuarioJpaRepository jpa;

    public UsuarioPersistenceAdapter(UsuarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Usuario salvar(Usuario usuario) {
        UsuarioEntity e = usuario.id() != null ? jpa.findById(usuario.id()).orElseThrow() : new UsuarioEntity();
        e.nome = usuario.nome();
        e.email = usuario.email();
        e.senhaHash = usuario.senhaHash();
        e.papel = usuario.papel().name();
        e.ativo = usuario.ativo();
        return paraDominio(jpa.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return jpa.findById(id).map(UsuarioPersistenceAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return jpa.findByEmail(email).map(UsuarioPersistenceAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeAlgum() {
        return jpa.count() > 0;
    }

    static Usuario paraDominio(UsuarioEntity e) {
        return Usuario.reconstituir(e.id, e.nome, e.email, e.senhaHash, Papel.valueOf(e.papel), e.ativo);
    }
}
