package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecorrenciaJpaRepository extends JpaRepository<RecorrenciaEntity, Long> {

    List<RecorrenciaEntity> findByFamiliaId(Long familiaId);

    Optional<RecorrenciaEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    boolean existsByCategoriaId(Long categoriaId);
}
