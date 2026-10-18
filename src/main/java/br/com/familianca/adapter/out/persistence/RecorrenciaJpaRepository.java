package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.DescricaoUsada;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecorrenciaJpaRepository extends JpaRepository<RecorrenciaEntity, Long> {

    List<RecorrenciaEntity> findByFamiliaId(Long familiaId);

    Optional<RecorrenciaEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    boolean existsByCategoriaId(Long categoriaId);

    /** Mesma busca de {@link LancamentoJpaRepository#buscarDescricoes}, nas recorrências. */
    @Query("""
            select new br.com.familianca.application.port.out.DescricaoUsada(
                r.descricao, max(semelhanca(:termo, sem_acento(lower(r.descricao)))), count(r))
            from RecorrenciaEntity r
            where r.familiaId = :familiaId and r.categoriaId = :categoriaId
              and (sem_acento(lower(r.descricao)) like :contem escape '\\'
                   or semelhanca(:termo, sem_acento(lower(r.descricao))) >= :minimo)
            group by r.descricao
            order by max(semelhanca(:termo, sem_acento(lower(r.descricao)))) desc, count(r) desc""")
    List<DescricaoUsada> buscarDescricoes(@Param("familiaId") Long familiaId, @Param("categoriaId") Long categoriaId,
                                          @Param("termo") String termo, @Param("contem") String contem,
                                          @Param("minimo") double minimo, Pageable limite);
}
