package br.com.familianca.adapter.in.seguranca;

import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Usuario;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Principal guardado na sessão (serializado na tabela spring_session_attributes), com a família e o papel nela. O
 * hash da senha é apagado logo após a autenticação (eraseCredentials), então não vai para a sessão. Papel ou família
 * mudou: as sessões do usuário são encerradas (SessoesUsuario).
 *
 * <p>Autoridades: ROLE_ADMIN/ROLE_USER (plataforma) e ROLE_DONO/ROLE_MEMBRO (família).
 */
public final class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String nome;
    private final String email;
    private final String papel;
    private final Long familiaId;
    private final Long membroId;
    private final String papelFamilia;
    private final boolean ativo;
    private String senhaHash;

    public UsuarioAutenticado(Long id, String nome, String email, String papel, Long familiaId, Long membroId,
                              String papelFamilia, boolean ativo, String senhaHash) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.papel = papel;
        this.familiaId = familiaId;
        this.membroId = membroId;
        this.papelFamilia = papelFamilia;
        this.ativo = ativo;
        this.senhaHash = senhaHash;
    }

    /** Usuário inativo ou membro inativo: não entra. */
    public static UsuarioAutenticado de(Usuario u, Membro m) {
        return new UsuarioAutenticado(u.id(), u.nome(), u.email(), u.papel().name(), m.familiaId(), m.id(),
                m.papel().name(), u.ativo() && m.ativo(), u.senhaHash());
    }

    public Long id() { return id; }
    public String nome() { return nome; }
    public String email() { return email; }
    public String papel() { return papel; }
    public Long familiaId() { return familiaId; }
    public Long membroId() { return membroId; }
    public String papelFamilia() { return papelFamilia; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + papel), new SimpleGrantedAuthority("ROLE_" + papelFamilia));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }

    @Override
    public void eraseCredentials() {
        senhaHash = null;
    }

    @Override
    public String toString() {
        return "UsuarioAutenticado[id=" + id + "]";
    }
}
