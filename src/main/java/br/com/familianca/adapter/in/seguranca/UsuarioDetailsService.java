package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.model.Usuario;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class UsuarioDetailsService implements UserDetailsService {

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
}
