package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ImportacaoIgnoradaJpaRepository extends JpaRepository<ImportacaoIgnoradaEntity, Long> {

    @Query("""
            select i.chave from ImportacaoIgnoradaEntity i
            where i.familiaId = :familiaId and i.localSaldoId = :localId and i.chave in :chaves""")
    List<String> chavesIgnoradas(@Param("familiaId") Long familiaId, @Param("localId") Long localId,
                                 @Param("chaves") Collection<String> chaves);
}
