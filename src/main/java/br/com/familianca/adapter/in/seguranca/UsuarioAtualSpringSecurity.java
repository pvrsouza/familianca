package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.model.PapelFamilia;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Sem usuário autenticado, falha (401): nenhum repositório consulta sem saber de que família é. */
@Component
public class UsuarioAtualSpringSecurity implements UsuarioAtual {

    @Override
    public Long id() {
        return principal().id();
    }

    @Override
    public Long familiaId() {
        return principal().familiaId();
    }

    @Override
    public Long membroId() {
        return principal().membroId();
    }

    @Override
    public PapelFamilia papel() {
        return PapelFamilia.valueOf(principal().papelFamilia());
    }

    private static UsuarioAutenticado principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return u;
        }
        throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado");
    }
}
