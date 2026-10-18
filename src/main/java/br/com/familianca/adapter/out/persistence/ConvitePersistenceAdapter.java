package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.ConviteRepository;
import br.com.familianca.domain.model.Convite;
import br.com.familianca.domain.model.PapelFamilia;
import br.com.familianca.domain.model.StatusConvite;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class ConvitePersistenceAdapter implements ConviteRepository {

    private final ConviteJpaRepository jpa;

    public ConvitePersistenceAdapter(ConviteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Convite salvar(Convite c) {
        ConviteEntity e = c.id() != null ? jpa.findById(c.id()).orElseThrow() : new ConviteEntity();
        e.familiaId = c.familiaId();
        e.email = c.email();
        e.papel = c.papel().name();
        e.tokenHash = c.tokenHash();
        e.status = c.status().name();
        e.expiraEm = c.expiraEm();
        e.convidadoPor = c.convidadoPor();
        // Flush para o índice único (um pendente por e-mail) valer na ordem: cancelar o expirado antes do novo.
        return paraDominio(jpa.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Convite> buscarPorTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(ConvitePersistenceAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Convite> buscar(Long familiaId, Long id) {
        return jpa.findByIdAndFamiliaId(id, familiaId).map(ConvitePersistenceAdapter::paraDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Convite> listarPendentes(Long familiaId, OffsetDateTime agora) {
        return jpa.findByFamiliaIdAndStatusAndExpiraEmAfterOrderByCriadoEm(familiaId, StatusConvite.PENDENTE.name(),
                agora).stream().map(ConvitePersistenceAdapter::paraDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Convite> buscarPendente(Long familiaId, String email) {
        return jpa.findByFamiliaIdAndEmailAndStatus(familiaId, email, StatusConvite.PENDENTE.name())
                .map(ConvitePersistenceAdapter::paraDominio);
    }

    private static Convite paraDominio(ConviteEntity e) {
        return Convite.reconstituir(e.id, e.familiaId, e.email, PapelFamilia.valueOf(e.papel), e.tokenHash,
                StatusConvite.valueOf(e.status), e.expiraEm, e.convidadoPor);
    }
}
