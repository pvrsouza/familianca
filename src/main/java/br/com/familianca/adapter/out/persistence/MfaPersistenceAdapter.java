package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.MfaRepository;
import br.com.familianca.application.port.out.Relogio;
import br.com.familianca.domain.model.Mfa;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class MfaPersistenceAdapter implements MfaRepository {

    private final MfaJpaRepository jpa;
    private final MfaCodigoRecuperacaoJpaRepository codigos;
    private final CifraSegredos cifra;
    private final Relogio relogio;

    public MfaPersistenceAdapter(MfaJpaRepository jpa, MfaCodigoRecuperacaoJpaRepository codigos, CifraSegredos cifra,
                                 Relogio relogio) {
        this.jpa = jpa;
        this.codigos = codigos;
        this.cifra = cifra;
        this.relogio = relogio;
    }

    @Override
    public boolean disponivel() {
        return cifra.disponivel();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Mfa> buscar(Long usuarioId) {
        return jpa.findById(usuarioId)
                .map(e -> Mfa.reconstituir(e.usuarioId, cifra.decifrar(e.segredoCifrado), e.ativo, e.ultimoPasso));
    }

    @Override
    public void salvar(Mfa mfa) {
        MfaEntity e = jpa.findById(mfa.usuarioId()).orElseGet(MfaEntity::new);
        // Só cifra de novo quando o segredo muda (o GCM gera um texto diferente a cada vez).
        if (e.segredoCifrado == null || !cifra.decifrar(e.segredoCifrado).equals(mfa.segredo())) {
            e.segredoCifrado = cifra.cifrar(mfa.segredo());
        }
        e.usuarioId = mfa.usuarioId();
        e.ativo = mfa.ativo();
        e.ultimoPasso = mfa.ultimoPasso();
        jpa.save(e);
    }

    @Override
    public void remover(Long usuarioId) {
        codigos.deleteByUsuarioId(usuarioId);
        jpa.deleteById(usuarioId);
    }

    @Override
    public void trocarCodigosRecuperacao(Long usuarioId, List<String> hashes) {
        codigos.deleteByUsuarioId(usuarioId);
        codigos.flush();
        codigos.saveAll(hashes.stream().map(h -> {
            MfaCodigoRecuperacaoEntity c = new MfaCodigoRecuperacaoEntity();
            c.usuarioId = usuarioId;
            c.codigoHash = h;
            return c;
        }).toList());
    }

    @Override
    public boolean usarCodigoRecuperacao(Long usuarioId, String hash) {
        return codigos.marcarUsado(usuarioId, hash, relogio.agora()) == 1;
    }
}
