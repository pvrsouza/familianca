package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.model.Usuario;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UsuarioDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final UsuarioRepository usuarios;
    private final MembroRepository membros;

    public UsuarioDetailsService(UsuarioRepository usuarios, MembroRepository membros) {
        this.usuarios = usuarios;
        this.membros = membros;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Usuario usuario = usuarios.buscarPorEmail(Usuario.normalizarEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
        return membros.buscarPorUsuario(usuario.id())
                .map(m -> UsuarioAutenticado.de(usuario, m))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    /**
     * Chamado no login certo quando o hash não é o atual (sem prefixo, ou BCrypt de outro custo): grava o hash novo.
     * Ver o {@code DelegatingPasswordEncoder} em {@link SecurityConfig}.
     */
    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails usuario, String novoHash) {
        Usuario atual = usuarios.buscarPorEmail(Usuario.normalizarEmail(usuario.getUsername()))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
        atual.trocarSenha(novoHash);
        usuarios.salvar(atual);
        return loadUserByUsername(atual.email());
    }
}
