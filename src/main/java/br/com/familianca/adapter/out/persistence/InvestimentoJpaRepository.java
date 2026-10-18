package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvestimentoJpaRepository extends JpaRepository<InvestimentoEntity, Long> {

    List<InvestimentoEntity> findByFamiliaId(Long familiaId);

    Optional<InvestimentoEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    @Query("""
            select count(i) > 0 from InvestimentoEntity i
            where i.familiaId = :familiaId and lower(i.nome) = lower(:nome) and i.id <> :ignorarId""")
    boolean existeNome(@Param("familiaId") Long familiaId, @Param("nome") String nome,
                       @Param("ignorarId") Long ignorarId);
}
