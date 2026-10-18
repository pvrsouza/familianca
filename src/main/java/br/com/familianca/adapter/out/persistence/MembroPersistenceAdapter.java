package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.MembroRepository;
import br.com.familianca.domain.model.Membro;
import br.com.familianca.domain.model.PapelFamilia;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Nome e e-mail do membro vêm do usuário. */
@Component
@Transactional
public class MembroPersistenceAdapter implements MembroRepository {

    private final MembroJpaRepository membros;
    private final UsuarioJpaRepository usuarios;

    public MembroPersistenceAdapter(MembroJpaRepository membros, UsuarioJpaRepository usuarios) {
        this.membros = membros;
        this.usuarios = usuarios;
    }

    @Override
    public Membro salvar(Membro membro) {
        MembroEntity e = membro.id() != null ? membros.findById(membro.id()).orElseThrow() : new MembroEntity();
        e.familiaId = membro.familiaId();
        e.usuarioId = membro.usuarioId();
        e.papel = membro.papel().name();
        e.ativo = membro.ativo();
        MembroEntity salvo = membros.save(e);
        return paraDominio(salvo, usuarios.findById(salvo.usuarioId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membro> buscarPorUsuario(Long usuarioId) {
        return membros.findByUsuarioId(usuarioId).map(this::comUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membro> buscar(Long familiaId, Long id) {
        return membros.findByIdAndFamiliaId(id, familiaId).map(this::comUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membro> listar(Long familiaId) {
        List<MembroEntity> daFamilia = membros.findByFamiliaId(familiaId);
        Map<Long, UsuarioEntity> porId = usuarios.findAllById(daFamilia.stream().map(m -> m.usuarioId).toList())
                .stream().collect(Collectors.toMap(u -> u.id, Function.identity()));
        return daFamilia.stream()
                .map(m -> paraDominio(m, porId.get(m.usuarioId)))
                .sorted(Comparator.comparing(Membro::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long contarAtivos(Long familiaId) {
        return membros.countByFamiliaIdAndAtivoTrue(familiaId);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarDonosAtivos(Long familiaId) {
        return membros.countByFamiliaIdAndPapelAndAtivoTrue(familiaId, PapelFamilia.DONO.name());
    }

    private Membro comUsuario(MembroEntity e) {
        return paraDominio(e, usuarios.findById(e.usuarioId).orElseThrow());
    }

    private static Membro paraDominio(MembroEntity e, UsuarioEntity u) {
        return new Membro(e.id, e.familiaId, e.usuarioId, u.nome, u.email, PapelFamilia.valueOf(e.papel), e.ativo);
    }
}
