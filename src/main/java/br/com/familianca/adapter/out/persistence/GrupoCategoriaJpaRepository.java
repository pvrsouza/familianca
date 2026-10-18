package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrupoCategoriaJpaRepository extends JpaRepository<GrupoCategoriaEntity, Long> {

    List<GrupoCategoriaEntity> findByFamiliaId(Long familiaId);

    Optional<GrupoCategoriaEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    @Query("""
            select count(g) > 0 from GrupoCategoriaEntity g
            where g.familiaId = :familiaId and g.tipo = :tipo and lower(g.nome) = lower(:nome) and g.id <> :ignorarId""")
    boolean existeNome(@Param("familiaId") Long familiaId, @Param("tipo") String tipo, @Param("nome") String nome,
                       @Param("ignorarId") Long ignorarId);
}
