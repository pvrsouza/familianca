package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.LocalSaldoRepository;
import br.com.familianca.application.port.out.UsuarioAtual;
import br.com.familianca.domain.exception.NaoEncontradoException;
import br.com.familianca.domain.model.LocalSaldo;
import br.com.familianca.domain.model.TipoLocalSaldo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Lugares do saldo da família do usuário logado. */
@Component
@Transactional
public class LocalSaldoPersistenceAdapter implements LocalSaldoRepository {

    private static final long NENHUM = -1L;

    private final LocalSaldoJpaRepository jpa;
    private final UsuarioAtual usuario;

    public LocalSaldoPersistenceAdapter(LocalSaldoJpaRepository jpa, UsuarioAtual usuario) {
        this.jpa = jpa;
        this.usuario = usuario;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalSaldo> listar() {
        return jpa.findByFamiliaId(usuario.familiaId()).stream().map(LocalSaldoPersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalSaldo> buscar(Long id) {
        return jpa.findByIdAndFamiliaId(id, usuario.familiaId()).map(LocalSaldoPersistenceAdapter::paraDominio);
    }

    @Override
    public LocalSaldo salvar(LocalSaldo l) {
        LocalSaldoEntity e;
        if (l.id() != null) {
            e = jpa.findByIdAndFamiliaId(l.id(), usuario.familiaId())
                    .orElseThrow(() -> new NaoEncontradoException("Lugar do saldo", l.id()));
        } else {
            e = new LocalSaldoEntity();
            e.familiaId = usuario.familiaId();
        }
        e.nome = l.nome();
        e.tipo = l.tipo().name();
        e.membroId = l.membroId();
        e.ativo = l.ativo();
        return paraDominio(jpa.save(e));
    }

    @Override
    public void remover(Long id) {
        jpa.findByIdAndFamiliaId(id, usuario.familiaId()).ifPresent(jpa::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeNome(String nome, Long ignorarId) {
        return jpa.existeNome(usuario.familiaId(), nome.trim(), ignorarId != null ? ignorarId : NENHUM);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emUso(Long id) {
        return jpa.emUso(usuario.familiaId(), id);
    }

    private static LocalSaldo paraDominio(LocalSaldoEntity e) {
        return new LocalSaldo(e.id, e.nome, TipoLocalSaldo.valueOf(e.tipo), e.membroId, e.ativo);
    }
}
