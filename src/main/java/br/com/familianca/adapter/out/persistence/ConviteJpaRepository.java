package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ConviteJpaRepository extends JpaRepository<ConviteEntity, Long> {

    Optional<ConviteEntity> findByTokenHash(String tokenHash);

    Optional<ConviteEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    List<ConviteEntity> findByFamiliaIdAndStatusAndExpiraEmAfterOrderByCriadoEm(Long familiaId, String status,
                                                                               OffsetDateTime agora);

    Optional<ConviteEntity> findByFamiliaIdAndEmailAndStatus(Long familiaId, String email, String status);

    @Modifying
    @Query("delete from ConviteEntity c where c.expiraEm < :limite")
    int apagarExpiradosAntesDe(@Param("limite") OffsetDateTime limite);
}
