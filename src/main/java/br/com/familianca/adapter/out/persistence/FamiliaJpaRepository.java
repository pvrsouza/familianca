package br.com.familianca.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface FamiliaJpaRepository extends JpaRepository<FamiliaEntity, Long> {

    /** SELECT ... FOR UPDATE: serializa as escritas automáticas da mesma família. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FamiliaEntity> findTravadaById(Long id);
}
