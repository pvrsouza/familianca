package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImportacaoLoteJpaRepository extends JpaRepository<ImportacaoLoteEntity, Long> {

    List<ImportacaoLoteEntity> findByFamiliaIdOrderByCriadoEmDescIdDesc(Long familiaId);

    Optional<ImportacaoLoteEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ImportacaoLoteEntity l where l.id = :id and l.familiaId = :familiaId")
    int apagar(@Param("id") Long id, @Param("familiaId") Long familiaId);
}
