package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoJpaRepository extends JpaRepository<LancamentoEntity, Long> {

    List<LancamentoEntity> findByFamiliaIdAndDataBetween(Long familiaId, LocalDate de, LocalDate ate);

    Optional<LancamentoEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    List<LancamentoEntity> findByFamiliaIdAndParcelamentoId(Long familiaId, UUID parcelamentoId);

    /** Competências AAAA-MM comparam certo como texto. */
    List<LancamentoEntity> findByFamiliaIdAndCompetenciaRecorrenciaBetween(Long familiaId, String de, String ate);

    boolean existsByRecorrenciaIdAndCompetenciaRecorrencia(Long recorrenciaId, String competencia);

    boolean existsByRecorrenciaId(Long recorrenciaId);

    boolean existsByCategoriaId(Long categoriaId);
}
