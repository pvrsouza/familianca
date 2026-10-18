package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoriaJpaRepository extends JpaRepository<CategoriaEntity, Long> {

    List<CategoriaEntity> findByFamiliaId(Long familiaId);

    Optional<CategoriaEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    boolean existsByGrupoId(Long grupoId);

    @Query("""
            select count(c) > 0 from CategoriaEntity c
            where c.grupoId = :grupoId and lower(c.nome) = lower(:nome) and c.id <> :ignorarId""")
    boolean existeNome(@Param("grupoId") Long grupoId, @Param("nome") String nome, @Param("ignorarId") Long ignorarId);
}
