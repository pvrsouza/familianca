package br.com.familianca.application.service;

import br.com.familianca.application.port.in.VerificarSegundoFator;
import br.com.familianca.application.port.out.MfaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.model.Mfa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sem @PreAuthorize: roda no login, antes de existir a sessão. */
@Service
@Transactional
public class SegundoFatorService implements VerificarSegundoFator {

    private static final Logger log = LoggerFactory.getLogger(SegundoFatorService.class);

    private final MfaRepository mfas;
    private final Relogio relogio;

    public SegundoFatorService(MfaRepository mfas, Relogio relogio) {
        this.mfas = mfas;
        this.relogio = relogio;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exigido(Long usuarioId) {
        return mfas.buscar(usuarioId).map(Mfa::ativo).orElse(false);
    }

    @Override
    public boolean confere(Long usuarioId, String codigo) {
        Mfa mfa = mfas.buscar(usuarioId).filter(Mfa::ativo).orElse(null);
        if (mfa == null || codigo == null || codigo.isBlank()) {
            return false;
        }
        if (mfa.verificar(codigo, relogio.agora().toInstant())) {
            mfas.salvar(mfa);
            return true;
        }
        if (mfas.usarCodigoRecuperacao(usuarioId, CodigosRecuperacao.hash(codigo))) {
            log.info("auditoria evento=CODIGO_RECUPERACAO_USADO usuarioId={}", usuarioId);
            return true;
        }
        return false;
    }
}
