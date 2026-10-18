package br.com.familianca.application.service;

import br.com.familianca.application.port.in.GerenciarMfa;
import br.com.familianca.application.port.in.VerificarSegundoFator;
import br.com.familianca.application.port.out.MfaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.application.port.out.SessoesUsuario;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.application.port.out.UsuarioRepository;
import br.com.familianca.domain.exception.ConflitoException;
import br.com.familianca.domain.exception.RegraNegocioException;
import br.com.familianca.domain.model.Mfa;
import br.com.familianca.domain.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class MfaService implements GerenciarMfa {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final MfaRepository mfas;
    private final UsuarioRepository usuarios;
    private final UsuarioAtual usuarioAtual;
    private final VerificarSegundoFator segundoFator;
    private final PasswordEncoder senhas;
    private final SessoesUsuario sessoes;
    private final Relogio relogio;
    private final String emissor;

    public MfaService(MfaRepository mfas, UsuarioRepository usuarios, UsuarioAtual usuarioAtual,
                      VerificarSegundoFator segundoFator, PasswordEncoder senhas, SessoesUsuario sessoes,
                      Relogio relogio, @Value("${familianca.seguranca.mfa.emissor:Familiança}") String emissor) {
        this.mfas = mfas;
        this.usuarios = usuarios;
        this.usuarioAtual = usuarioAtual;
        this.segundoFator = segundoFator;
        this.senhas = senhas;
        this.sessoes = sessoes;
        this.relogio = relogio;
        this.emissor = emissor;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean ativo() {
        return segundoFator.exigido(usuarioAtual.id());
    }

    @Override
    public boolean disponivel() {
        return mfas.disponivel();
    }

    @Override
    public ConfiguracaoMfa configurar() {
        if (!mfas.disponivel()) {
            throw new RegraNegocioException("A verificação em duas etapas ainda não está disponível");
        }
        Usuario usuario = usuarios.buscarPorId(usuarioAtual.id()).orElseThrow();
        if (segundoFator.exigido(usuario.id())) {
            throw new ConflitoException("A verificação em duas etapas já está ativa");
        }
        Mfa mfa = Mfa.novo(usuario.id());
        mfas.salvar(mfa);
        return new ConfiguracaoMfa(mfa.segredo(), mfa.uri(emissor, usuario.email()));
    }

    @Override
    public List<String> ativar(String codigo) {
        Usuario usuario = usuarios.buscarPorId(usuarioAtual.id()).orElseThrow();
        Mfa mfa = mfas.buscar(usuario.id())
                .orElseThrow(() -> new RegraNegocioException("Configure o aplicativo autenticador antes de ativar"));
        mfa.ativar(codigo, relogio.agora().toInstant());
        mfas.salvar(mfa);
        List<String> codigos = novosCodigos(usuario.id());
        sessoes.encerrarOutras(usuario.email());
        log.info("auditoria evento=MFA_ATIVADO usuarioId={}", usuario.id());
        return codigos;
    }

    @Override
    public List<String> gerarCodigosRecuperacao(String codigo) {
        Long id = usuarioAtual.id();
        Mfa mfa = mfas.buscar(id).filter(Mfa::ativo)
                .orElseThrow(() -> new RegraNegocioException("A verificação em duas etapas não está ativa"));
        if (!mfa.verificar(codigo, relogio.agora().toInstant())) {
            throw new RegraNegocioException("Código inválido");
        }
        mfas.salvar(mfa);
        log.info("auditoria evento=MFA_CODIGOS_RECUPERACAO_TROCADOS usuarioId={}", id);
        return novosCodigos(id);
    }

    @Override
    public void desativar(String senha, String codigo) {
        Usuario usuario = usuarios.buscarPorId(usuarioAtual.id()).orElseThrow();
        if (!segundoFator.exigido(usuario.id())) {
            throw new RegraNegocioException("A verificação em duas etapas não está ativa");
        }
        if (senha == null || !senhas.matches(senha, usuario.senhaHash()) || !segundoFator.confere(usuario.id(), codigo)) {
            throw new RegraNegocioException("Senha ou código incorreto");
        }
        mfas.remover(usuario.id());
        log.info("auditoria evento=MFA_DESATIVADO usuarioId={}", usuario.id());
    }

    private List<String> novosCodigos(Long usuarioId) {
        List<String> codigos = CodigosRecuperacao.gerar();
        mfas.trocarCodigosRecuperacao(usuarioId, codigos.stream().map(CodigosRecuperacao::hash).toList());
        return codigos;
    }
}
