package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConviteJpaRepository extends JpaRepository<ConviteEntity, Long> {

    Optional<ConviteEntity> findByTokenHash(String tokenHash);

    Optional<ConviteEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    List<ConviteEntity> findByFamiliaIdAndStatusAndExpiraEmAfterOrderByCriadoEm(Long familiaId, String status,
                                                                               OffsetDateTime agora);

    Optional<ConviteEntity> findByFamiliaIdAndEmailAndStatus(Long familiaId, String email, String status);
}
