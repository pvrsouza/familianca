package br.com.familianca.adapter.out.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Exclusões em lote de tudo o que é da família, na ordem das chaves estrangeiras. As exceções das recorrências e os
 * movimentos dos investimentos saem junto (on delete cascade), mas os movimentos vão explicitamente antes.
 */
public interface DadosFamiliaJpaRepository extends Repository<FamiliaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LancamentoEntity l where l.familiaId = :familiaId")
    int apagarLancamentos(@Param("familiaId") Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecorrenciaEntity r where r.familiaId = :familiaId")
    int apagarRecorrencias(@Param("familiaId") Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SaldoMesEntity s where s.familiaId = :familiaId")
    int apagarSaldos(@Param("familiaId") Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from MovimentoInvestimentoEntity m
            where m.investimentoId in (select i.id from InvestimentoEntity i where i.familiaId = :familiaId)""")
    int apagarMovimentosInvestimento(@Param("familiaId") Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from InvestimentoEntity i where i.familiaId = :familiaId")
    int apagarInvestimentos(@Param("familiaId") Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CategoriaEntity c where c.familiaId = :familiaId")
    int apagarCategorias(@Param("familiaId") Long familiaId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GrupoCategoriaEntity g where g.familiaId = :familiaId")
    int apagarGrupos(@Param("familiaId") Long familiaId);
}
