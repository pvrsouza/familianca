package br.com.familianca.application.service;

import br.com.familianca.application.port.in.AceitarConvite;
import br.com.familianca.application.port.out.ConviteRepository;
import br.com.familianca.application.port.out.FamiliaRepository;
import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.exception.RecursoForaDoPlanoException;
import br.com.familianca.domain.model.Convite;
import br.com.familianca.domain.model.Familia;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Papel;
import br.com.familianca.domain.model.Plano;
import br.com.familianca.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sem @PreAuthorize: o link do convite é aberto por quem ainda não tem conta. */
@Service
@Transactional
public class ConviteService implements AceitarConvite {

    private static final Logger log = LoggerFactory.getLogger(ConviteService.class);

    private final ConviteRepository convites;
    private final FamiliaRepository familias;
    private final MembroRepository membros;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final Relogio relogio;

    public ConviteService(ConviteRepository convites, FamiliaRepository familias, MembroRepository membros,
                          UsuarioRepository usuarios, PasswordEncoder senhas, Relogio relogio) {
        this.convites = convites;
        this.familias = familias;
        this.membros = membros;
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public ConviteAberto consultar(String token) {
        Convite convite = exigirValido(token);
        Familia familia = familias.buscarPorId(convite.familiaId()).orElseThrow();
        return new ConviteAberto(familia.nome(), convite.email(), convite.papel(), convite.expiraEm());
    }

    @Override
    public Membro aceitar(String token, String nome, String senha) {
        Convite convite = exigirValido(token);
        Usuario.validarSenha(senha);
        if (usuarios.buscarPorEmail(convite.email()).isPresent()) {
            throw new ConflitoException("Já existe uma conta com este e-mail");
        }
        Familia familia = familias.buscarPorId(convite.familiaId()).orElseThrow();
        Plano plano = familia.assinatura().planoEmVigor(relogio.hoje());
        if (membros.contarAtivos(familia.id()) >= plano.limiteMembros()) {
            throw new RecursoForaDoPlanoException(plano, null,
                    "A família atingiu o limite de " + plano.limiteMembros() + " pessoas do plano " + plano.rotulo());
        }
        convite.aceitar(relogio.agora());
        convites.salvar(convite);
        Usuario usuario = usuarios.salvar(Usuario.novo(nome, convite.email(), senhas.encode(senha), Papel.USER));
        Membro membro = membros.salvar(Membro.novo(familia.id(), usuario.id(), convite.papel()));
        log.info("auditoria evento=CONVITE_ACEITO usuarioId={} familiaId={} conviteId={}", usuario.id(),
                familia.id(), convite.id());
        return membro;
    }

    private Convite exigirValido(String token) {
        return convites.buscarPorTokenHash(TokensConvite.hash(token))
                .filter(c -> c.valido(relogio.agora()))
                .orElseThrow(() -> new NaoEncontradoException("Convite não encontrado ou expirado"));
    }
}
