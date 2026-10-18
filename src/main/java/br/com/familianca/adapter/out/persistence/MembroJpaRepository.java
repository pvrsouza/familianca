package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembroJpaRepository extends JpaRepository<MembroEntity, Long> {

    Optional<MembroEntity> findByUsuarioId(Long usuarioId);

    Optional<MembroEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    List<MembroEntity> findByFamiliaId(Long familiaId);

    long countByFamiliaIdAndAtivoTrue(Long familiaId);

    long countByFamiliaIdAndPapelAndAtivoTrue(Long familiaId, String papel);
}
