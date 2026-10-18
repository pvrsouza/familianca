package br.com.familianca.application.service;

import br.com.familianca.application.port.in.PrepararAcesso;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.Papel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sem @PreAuthorize: roda no startup, antes de existir qualquer login. Não é exposto na API. */
@Service
@Transactional
public class PrepararAcessoService implements PrepararAcesso {

    private static final Logger log = LoggerFactory.getLogger(PrepararAcessoService.class);

    private final UsuarioRepository usuarios;
    private final CadastroService cadastro;

    public PrepararAcessoService(UsuarioRepository usuarios, CadastroService cadastro) {
        this.usuarios = usuarios;
        this.cadastro = cadastro;
    }

    @Override
    public void prepararPrimeiroAcesso(String nome, String email, String senha) {
        if (usuarios.existeAlgum() || email == null || email.isBlank() || senha == null || senha.isEmpty()) {
            return;
        }
        Membro admin = cadastro.criar(nome, email, senha, null, Papel.ADMIN);
        log.info("auditoria evento=ADMIN_INICIAL_CRIADO usuarioId={}", admin.usuarioId());
    }
}
