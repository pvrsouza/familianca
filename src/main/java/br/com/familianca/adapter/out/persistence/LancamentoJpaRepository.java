package br.com.familianca.adapter.out.persistence;

import br.com.familianca.application.port.out.DescricaoUsada;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Descrições já usadas na categoria: as mais frequentes primeiro e, no empate, as mais recentes. */
    @Query("""
            select l.descricao from LancamentoEntity l
            where l.familiaId = :familiaId and l.categoriaId = :categoriaId
            group by l.descricao
            order by count(l) desc, max(l.data) desc""")
    List<String> descricoesMaisUsadas(@Param("familiaId") Long familiaId, @Param("categoriaId") Long categoriaId,
                                      Pageable limite);

    /**
     * Descrições da categoria que contêm o termo (sem acento, minúsculo) ou se parecem com ele (pg_trgm), as mais
     * parecidas e mais usadas primeiro. contem = "%termo%" já escapado (ver {@link FuncoesBusca#contem}).
     */
    @Query("""
            select new br.com.familianca.application.port.out.DescricaoUsada(
                l.descricao, max(semelhanca(:termo, sem_acento(lower(l.descricao)))), count(l))
            from LancamentoEntity l
            where l.familiaId = :familiaId and l.categoriaId = :categoriaId
              and (sem_acento(lower(l.descricao)) like :contem escape '\\'
                   or semelhanca(:termo, sem_acento(lower(l.descricao))) >= :minimo)
            group by l.descricao
            order by max(semelhanca(:termo, sem_acento(lower(l.descricao)))) desc, count(l) desc""")
    List<DescricaoUsada> buscarDescricoes(@Param("familiaId") Long familiaId, @Param("categoriaId") Long categoriaId,
                                          @Param("termo") String termo, @Param("contem") String contem,
                                          @Param("minimo") double minimo, Pageable limite);
}
