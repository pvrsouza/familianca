package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocalSaldoJpaRepository extends JpaRepository<LocalSaldoEntity, Long> {

    List<LocalSaldoEntity> findByFamiliaId(Long familiaId);

    Optional<LocalSaldoEntity> findByIdAndFamiliaId(Long id, Long familiaId);

    @Query("""
            select count(l) > 0 from LocalSaldoEntity l
            where l.familiaId = :familiaId and lower(l.nome) = lower(:nome) and l.id <> :ignorarId""")
    boolean existeNome(@Param("familiaId") Long familiaId, @Param("nome") String nome,
                       @Param("ignorarId") Long ignorarId);

    @Query("""
            select count(s) > 0 from SaldoMesEntity s join s.porLocal p
            where s.familiaId = :familiaId and key(p) = :localId""")
    boolean emUso(@Param("familiaId") Long familiaId, @Param("localId") Long localId);
}
