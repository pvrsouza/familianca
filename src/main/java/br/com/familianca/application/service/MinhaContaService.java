package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarMinhaConta;
import br.com.familianca.application.port.out.SessoesUsuario;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Usuario;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class MinhaContaService implements GerenciarMinhaConta {

    private final UsuarioRepository usuarios;
    private final UsuarioAtual usuarioAtual;
    private final PasswordEncoder senhas;
    private final SessoesUsuario sessoes;

    public MinhaContaService(UsuarioRepository usuarios, UsuarioAtual usuarioAtual, PasswordEncoder senhas,
                             SessoesUsuario sessoes) {
        this.usuarios = usuarios;
        this.usuarioAtual = usuarioAtual;
        this.senhas = senhas;
        this.sessoes = sessoes;
    }

    @Override
    public void trocarSenha(String senhaAtual, String novaSenha) {
        Usuario usuario = usuarios.buscarPorId(usuarioAtual.id()).orElseThrow();
        if (senhaAtual == null || !senhas.matches(senhaAtual, usuario.senhaHash())) {
            throw new RegraNegocioException("Senha atual incorreta");
        }
        Usuario.validarSenha(novaSenha);
        if (novaSenha.equals(senhaAtual)) {
            throw new RegraNegocioException("A nova senha precisa ser diferente da atual");
        }
        usuario.trocarSenha(senhas.encode(novaSenha));
        usuarios.salvar(usuario);
        sessoes.encerrarOutras(usuario.email());
    }

    @Override
    public void renomear(String nome) {
        Usuario usuario = usuarios.buscarPorId(usuarioAtual.id()).orElseThrow();
        usuario.renomear(nome);
        usuarios.salvar(usuario);
    }
}
